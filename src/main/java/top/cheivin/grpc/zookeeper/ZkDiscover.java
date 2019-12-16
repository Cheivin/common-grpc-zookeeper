package top.cheivin.grpc.zookeeper;

import top.cheivin.grpc.core.Discover;
import top.cheivin.grpc.core.GrpcRequest;
import top.cheivin.grpc.core.RemoteInstance;
import top.cheivin.grpc.exception.ChannelException;
import top.cheivin.grpc.exception.InstanceException;
import top.cheivin.grpc.loadbalance.LoadBalance;
import top.cheivin.grpc.loadbalance.LoadBalanceFactory;
import lombok.extern.slf4j.Slf4j;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

/**
 *
 */
@Slf4j
public class ZkDiscover implements Discover, Watcher {
    private String appName;
    private String zkHost;
    private int timeout;
    /**
     * 负载均衡方式
     */
    private int loadBalanceType;
    /**
     * 等待连接成功的信号
     */
    private CountDownLatch connectedSemaphore = new CountDownLatch(1);
    private ZooKeeper zk;

    /**
     * 记录当前服务节点
     */
    private ConcurrentHashMap<String, Provider> providerNodes = new ConcurrentHashMap<>();

    public ZkDiscover() {
        this("localhost");
    }

    public ZkDiscover(String zkHost) {
        this(zkHost, 10000);
    }

    public ZkDiscover(String zkHost, int timeout) {
        this(zkHost, timeout, -1);
    }

    public ZkDiscover(String zkHost, int timeout, int loadBalanceType) {
        this("COMMON-gRPC", zkHost, timeout, loadBalanceType);
    }

    public ZkDiscover(String appName, String zkHost, int timeout, int loadBalanceType) {
        this.appName = "/" + appName;
        this.zkHost = zkHost;
        this.timeout = timeout;
        this.loadBalanceType = loadBalanceType;
    }

    @Override
    public void start() throws Exception {
        ZooKeeper zk = new ZooKeeper(this.zkHost, this.timeout, this);
        connectedSemaphore.await();
        log.info("{} connection success", "zookeeper");
        this.zk = zk;
        updateServiceList();
        log.info("services:{}", providerNodes);
    }

    @Override
    public void close() {
        try {
            zk.close();
        } catch (InterruptedException e) {
            log.error("zk discover stop error", e);
        }
        providerNodes.values().forEach(Provider::close);
        providerNodes.clear();
    }

    @Override
    public RemoteInstance getInstance(GrpcRequest request) throws InstanceException, ChannelException {
        Provider provider = providerNodes.get(request.getServiceName() + ":" + request.getVersion());
        if (provider == null) {
            throw new InstanceException("no instance exist");
        }
        RemoteInstance remoteInstance = provider.choose();
        if (remoteInstance == null) {
            throw new InstanceException("no instance choose");
        }
        if (remoteInstance.getChannel().isShutdown()) {
            throw new ChannelException("channel is shutdown");
        }
        return remoteInstance;
    }

    /**
     * 更新服务节点列表
     */
    private void updateServiceList() {
        try {
            List<String> services = zk.getChildren(appName, true);
            if (services.size() == 0) { // 无节点时
                providerNodes.values().forEach(Provider::close);
                providerNodes.clear();
            } else {
                /*
                 * 处理移除的服务
                 * 1.将本地map的服务key值放入临时set
                 * 2.将从zookeeper上获取到的服务节点key从临时set中移除
                 * 3.临时set中剩余的则是zookeeper中不存在的
                 * 4.调用移除方法将不存在的provider移除
                 */
                Set<String> serviceSet = new HashSet<>(providerNodes.keySet());
                serviceSet.removeAll(services);
                serviceSet.parallelStream().forEach(this::removeService);

                /*
                 * 处理新增的服务
                 * 1.将本地map的服务key值从zookeeper上获取到的服务节点list中移除
                 * 2.list中剩余的则是提供者节点有变动的服务
                 * 3.调用更新方法更新服务的提供者节点列表
                 */
                services.removeAll(providerNodes.keySet());
                services.parallelStream().forEach(this::updateProviderList);

            }
        } catch (KeeperException | InterruptedException e) {
            e.printStackTrace();
        }
    }


    /**
     * 移除服务
     *
     * @param serviceKey 服务key值
     */
    private void removeService(String serviceKey) {
        Provider provider = providerNodes.remove(serviceKey);
        provider.close();
    }

    /**
     * 更新服务提供者节点列表
     *
     * @param serviceKey 服务key值
     */
    private void updateProviderList(String serviceKey) {
        // 获取提供者管理器
        Provider provider = providerNodes.get(serviceKey);
        if (provider == null) {
            provider = new Provider(LoadBalanceFactory.getBalance(this.loadBalanceType));
            providerNodes.put(serviceKey, provider);
        }
        try {
            List<String> nodes = zk.getChildren(appName + "/" + serviceKey, true);
            if (nodes.size() == 0) { // 无节点时，关闭已连接的提供者
                provider.close();
            } else {
                // 处理节点id信息
                Map<String, RemoteInstance> instanceMap = new HashMap<>();
                nodes.forEach(node -> {
                    RemoteInstance instance = parseNodeName(node);
                    instanceMap.put(instance.getId(), instance);
                });

                /*
                 * 处理移除的节点
                 * 1.将服务的节点实例id列表放入临时set
                 * 2.将从zookeeper上获取到的节点实例id从临时set中移除
                 * 3.临时set中剩余的则是zookeeper中不存在的instance
                 * 4.调用移除方法将不存在的provider移除
                 */
                Set<String> instanceIds = provider.getInstanceIds();
                instanceIds.removeAll(instanceMap.keySet());
                for (String instanceId : instanceIds) {
                    provider.remove(instanceId);
                }

                /*
                 * 处理新增的节点
                 * 1.将本地节点实例id列表从zookeeper上获取到节点实例id的临时set中移除
                 * 2.临时set中剩余的则是有变动的instance
                 * 3.添加变动的instance至provider中
                 */
                instanceMap.keySet().removeAll(provider.getInstanceIds());
                for (RemoteInstance instance : instanceMap.values()) {
                    provider.add(instance);
                }
                /*for (String provider : providers) {
                    String[] hostInfo = provider.split(":");
                    ProviderContext context = new ProviderContext(hostInfo[0], Integer.parseInt(hostInfo[1]));
                    context.connect();
                    service.put(provider, context);
                }*/
            }
        } catch (KeeperException | InterruptedException e) {
            log.error("zk error", e);
        }
    }

    @Override
    public void process(WatchedEvent event) {
        switch (event.getType()) {
            case None:
                // 连接状态发生变化
                if (Event.KeeperState.SyncConnected.equals(event.getState())) {
                    // 连接建立成功
                    connectedSemaphore.countDown();
                }
                break;
            case NodeChildrenChanged:
                if (event.getPath().equals(appName)) {
                    log.info("服务列表变动:{}", event.getPath());
                    updateServiceList();
                } else if (event.getPath().startsWith(appName)) {
                    String serviceName = event.getPath().substring(appName.length() + 1);
                    log.info("服务提供者变动:{}", serviceName);
                    updateProviderList(serviceName);
                }
                log.info("结果:{}", providerNodes);
                break;
        }
    }

    static class Provider {
        private Set<String> instanceIds;
        private LoadBalance loadBalance;

        Provider(LoadBalance balance) {
            this.instanceIds = new HashSet<>();
            this.loadBalance = balance;
        }

        RemoteInstance choose() {
            return this.loadBalance.choose();
        }

        void close() {
            instanceIds.clear();
            loadBalance.destroy();
        }

        Set<String> getInstanceIds() {
            return new HashSet<>(instanceIds);
        }

        void remove(String instanceId) {
            if (instanceIds.remove(instanceId)) {
                loadBalance.removeInstance(instanceId);
            }
        }

        void add(RemoteInstance instance) {
            if (instanceIds.add(instance.getId())) {
                instance.connect();
                loadBalance.addInstance(instance);
            }
        }

        @Override
        public String toString() {
            return "Provider{" + loadBalance + '}';
        }
    }


    /**
     * 服务实例节点信息 服务ip@端口=权重:实例ID
     *
     * @param nodeName 节点名称
     * @return 服务节点信息
     */
    private RemoteInstance parseNodeName(String nodeName) {
        String[] nodeInfos = nodeName.split("=");
        String[] hostInfo = nodeInfos[0].split("@");
        String[] insInfo = nodeInfos[1].split(":");
        return RemoteInstance.builder()
                .id(insInfo[1])
                .ip(hostInfo[0])
                .port(Integer.parseInt(hostInfo[1]))
                .weight(Integer.parseInt(insInfo[0]))
                .build();
    }
}

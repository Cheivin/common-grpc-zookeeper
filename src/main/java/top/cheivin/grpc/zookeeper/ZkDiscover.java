package top.cheivin.grpc.zookeeper;

import lombok.extern.slf4j.Slf4j;
import org.apache.zookeeper.*;
import org.apache.zookeeper.data.Stat;
import top.cheivin.grpc.core.Discover;
import top.cheivin.grpc.core.GrpcRequest;
import top.cheivin.grpc.core.RemoteInstance;
import top.cheivin.grpc.core.RemoteInstanceManage;
import top.cheivin.grpc.exception.ChannelException;
import top.cheivin.grpc.exception.InstanceException;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
     * 等待连接成功的信号
     */
    private CountDownLatch connectedSemaphore = new CountDownLatch(1);
    private ZooKeeper zk;
    private boolean run;
    /**
     * 远程服务提供者实例管理器
     */
    private RemoteInstanceManage remoteInstanceManage;

    /**
     * 记录当前服务节点
     */
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

    public ZkDiscover(String appName, String zkHost, int timeout, RemoteInstanceManage remoteInstanceManage) {
        this.appName = "/" + appName;
        this.zkHost = zkHost;
        this.timeout = timeout;
        this.remoteInstanceManage = remoteInstanceManage;
    }

    public ZkDiscover(String appName, String zkHost, int timeout, int loadBalanceType) {
        this.appName = "/" + appName;
        this.zkHost = zkHost;
        this.timeout = timeout;
        this.remoteInstanceManage = new RemoteInstanceManage(loadBalanceType);
    }

    @Override
    public void start() throws Exception {
        ZooKeeper zk = new ZooKeeper(this.zkHost, this.timeout, this);
        connectedSemaphore.await();
        this.zk = zk;
        run = true;
        checkRootNode();
        updateServiceList();
    }

    private void checkRootNode() throws KeeperException, InterruptedException {
        Stat exists = zk.exists(appName, false);
        if (exists == null) {
            zk.create(appName, null, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
        }
    }


    @Override
    public void close() {
        try {
            if (zk != null) {
                run = false;
                zk.close();
            }
        } catch (InterruptedException e) {
            log.error("zk discover stop error", e);
        }
        remoteInstanceManage.clear();
    }

    @Override
    public RemoteInstance getInstance(GrpcRequest request) throws InstanceException, ChannelException {
        return remoteInstanceManage.getInstance(request.getServiceName() + ":" + request.getVersion());
    }

    /**
     * 更新服务节点列表
     */
    private void updateServiceList() {
        try {
            List<String> services = zk.getChildren(appName, true);
            if (services.size() == 0) { // 无节点时
                remoteInstanceManage.clear();
            } else {
                // 刷新服务列表
                String[][] changeArr = remoteInstanceManage.refreshServiceList(services);
                String[] newServiceKeys = changeArr[1];
                if (newServiceKeys.length > 0) {
                    Arrays.stream(newServiceKeys).parallel().forEach(this::updateProviderList);
                }
            }
        } catch (KeeperException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * 更新服务提供者节点列表
     *
     * @param serviceKey 服务key值
     */
    private void updateProviderList(String serviceKey) {
        try {
            List<String> nodes = zk.getChildren(appName + "/" + serviceKey, true);
            if (nodes.size() == 0) { // 无节点时，关闭已连接的提供者
                //remoteInstanceManage.clear(serviceKey);
            } else {
                // 处理节点id信息
                Map<String, RemoteInstance> instanceMap = new HashMap<>();
                nodes.forEach(node -> {
                    RemoteInstance instance = parseNodeName(node);
                    instanceMap.put(instance.getId(), instance);
                });
                // 刷新节点信息
                remoteInstanceManage.refreshInstances(serviceKey, instanceMap);
            }
        } catch (KeeperException | InterruptedException e) {
            if (run) {
                log.error("zk error", e);
            }
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
                log.info("service or instance changed");
                if (event.getPath().equals(appName)) {
                    updateServiceList();
                } else if (event.getPath().startsWith(appName)) {
                    String serviceName = event.getPath().substring(appName.length() + 1);
                    updateProviderList(serviceName);
                }
                break;
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

package top.cheivin.grpc.zookeeper;

import lombok.extern.slf4j.Slf4j;
import org.apache.zookeeper.*;
import org.apache.zookeeper.data.Stat;
import top.cheivin.grpc.core.Registry;
import top.cheivin.grpc.core.ServiceInfo;
import top.cheivin.grpc.util.NetWorkAddressUtils;

import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

/**
 * zookeeper注册器
 */
@Slf4j
public class ZkRegistry implements Registry, Watcher {
    private static final String ip = NetWorkAddressUtils.findLocalHostAddress();
    private String appName;
    private String zkHost;
    private int timeout;
    private int serverPort;

    /**
     * 等待连接成功的信号
     */
    private CountDownLatch connectedSemaphore = new CountDownLatch(1);
    private ZooKeeper zk;

    public ZkRegistry() {
        this("localhost");
    }

    public ZkRegistry(String zkHost) {
        this(zkHost, 10000);
    }

    public ZkRegistry(String zkHost, int timeout) {
        this("COMMON-gRPC", zkHost, timeout);
    }

    public ZkRegistry(String appName, String zkHost, int timeout) {
        this.appName = "/" + appName;
        this.timeout = timeout;
        this.zkHost = zkHost;
    }

    private void checkRootNode() throws KeeperException, InterruptedException {
        Stat exists = zk.exists(appName, false);
        if (exists == null) {
            log.info("Init application:{}", appName);
            zk.create(appName, null, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
        }
    }

    @Override
    public void start(int serverPort) throws Exception {
        this.serverPort = serverPort;
        ZooKeeper zk = new ZooKeeper(this.zkHost, this.timeout, this);
        connectedSemaphore.await();
        log.info("{} connection success", "zookeeper");
        this.zk = zk;
        checkRootNode();
    }

    @Override
    public void close() {
        try {
            if (zk!=null){
                zk.close();
            }
        } catch (Exception e) {
            log.error("zk registry stop error", e);
        }
    }

    @Override
    public void addService(Collection<ServiceInfo> infos) {
        for (ServiceInfo info : infos) {
            String zNode = appName + "/" + info.getServiceName() + ":" + info.getVersion();
            try {
                // 检查并创建服务节点
                if (zk.exists(zNode, false) == null) {
                    log.warn("service node not exists:{}", zNode);
                    zk.create(zNode, info.getAlias().getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
                    log.info("create service node:{}", zNode);
                }
                // 创建服务提供者节点,格式：根节点/
                String providerNode = zNode + "/" + getNodeName(info);
                zk.create(providerNode, info.getAlias().getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL);
                log.info("create service provider node:{}", providerNode);
            } catch (KeeperException | InterruptedException e) {
                log.error("registry error", e);
            }
        }
    }

    @Override
    public void process(WatchedEvent event) {
        if (Event.EventType.None.equals(event.getType())) {
            // 连接状态发生变化
            if (Event.KeeperState.SyncConnected.equals(event.getState())) {
                // 连接建立成功
                connectedSemaphore.countDown();
            }
        }
    }

    /**
     * 服务实例节点名称 服务ip@端口=权重:实例ID
     *
     * @param info 服务信息
     * @return 节点名称
     */
    private String getNodeName(ServiceInfo info) {
        return ip + "@" + serverPort + "=" + info.getWeight() + ":" + UUID.randomUUID().toString();
    }
}

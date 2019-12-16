package top.cheivin.grpc.zookeeper;

import lombok.extern.slf4j.Slf4j;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import top.cheivin.grpc.GrpcClient;
import top.cheivin.grpc.GrpcServer;
import top.cheivin.grpc.core.Discover;
import top.cheivin.grpc.core.GrpcRequest;
import top.cheivin.grpc.core.Registry;
import top.cheivin.grpc.exception.InstanceException;
import top.cheivin.grpc.exception.InvokeException;
import top.cheivin.grpc.handle.DefaultServiceInfoManage;
import top.cheivin.service.AService;
import top.cheivin.service.TestService;

import java.util.Random;

/**
 *
 */
@Slf4j
public class ZkTest {
    private GrpcServer server;

    @Before
    public void startServer() throws Exception {
        Registry client = new ZkRegistry();
        DefaultServiceInfoManage manage = new DefaultServiceInfoManage();
        manage.addService(AService.class);
        manage.addService(TestService.class);

        GrpcServer server = GrpcServer.from(client, manage)
                .port(10000 + new Random().nextInt(20000))
                .build();

        server.start();
        this.server = server;
    }

    @After
    public void stopServer() {
        this.server.stop();
    }

    @Test
    public void testBatchCall() throws Exception {
        Discover discover = new ZkDiscover();
        GrpcClient client = new GrpcClient(discover);
        client.start();
        for (int i = 0; i < 30; i++) {
            int finalI = i;
            (new Thread(() -> {
                GrpcRequest request = new GrpcRequest();
                request.setServiceName("testService");
                request.setMethodName("hello1");
                request.setVersion("1.0.0");
                request.setArgs(new Object[]{"cheivin" + finalI});
                try {
                    log.info("res:{}", client.invoke(request));
                } catch (InstanceException | InvokeException e) {
                    e.printStackTrace();
                }
            })).start();
        }
        client.stop();
    }

    @Test
    public void testMultiArgsCall() throws Exception {
        Discover discover = new ZkDiscover();
        GrpcClient client = new GrpcClient(discover);
        client.start();

        GrpcRequest request = new GrpcRequest();
        request.setServiceName("testService");
        request.setMethodName("hello2");
        request.setVersion("1.0.0");
        request.setArgs(new Object[]{"hahahaha", "cheivin"});
        Object res = client.invoke(request);
        log.info("res:{}", res);
        client.stop();
    }

    @Test
    public void testNoArgsCall() throws Exception {
        Discover discover = new ZkDiscover();
        GrpcClient client = new GrpcClient(discover);
        client.start();

        GrpcRequest request = new GrpcRequest();

        request.setServiceName("testService");
        request.setMethodName("hello");
        request.setVersion("1.0.0");
        request.setArgs(null);
        Object res = client.invoke(request);
        log.info("res:{}", res);
        client.stop();
    }

    @Test
    public void testServerChange() throws Exception {
        Discover discover = new ZkDiscover();
        GrpcClient client = new GrpcClient(discover);
        client.start();

        Registry registry = new ZkRegistry();
        DefaultServiceInfoManage manage = new DefaultServiceInfoManage();
        manage.addService(AService.class);
        manage.addService(TestService.class);

        GrpcServer server = GrpcServer.from(registry, manage)
                .port(10000 + new Random().nextInt(20000))
                .build();
        server.start();
        Thread.sleep(3000);
        server.stop();

        client.stop();
    }
}

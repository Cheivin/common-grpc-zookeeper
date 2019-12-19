package top.cheivin.grpc.zookeeper;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import top.cheivin.grpc.GrpcClient;
import top.cheivin.grpc.GrpcServer;
import top.cheivin.grpc.core.DefaultServiceInfoManage;
import top.cheivin.grpc.core.Discover;
import top.cheivin.grpc.core.GrpcRequest;
import top.cheivin.grpc.core.Registry;
import top.cheivin.grpc.exception.InstanceException;
import top.cheivin.grpc.exception.InvokeException;
import top.cheivin.service.AService;
import top.cheivin.service.TestService;

import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Slf4j
public class ZkTest {
    private GrpcServer server;

    @BeforeEach
    public void startServer() throws Exception {
        Registry client = new ZkRegistry();
        DefaultServiceInfoManage manage = new DefaultServiceInfoManage();
        manage.addService(AService.class);
        manage.addService(TestService.class);

        GrpcServer server = GrpcServer.from(client, manage)
                .port(10000 + new Random().nextInt(20000))
                .build();

        server.start();
        Thread.sleep(10000);
        this.server = server;
    }

    @AfterEach
    public void stopServer() {
        this.server.stop();
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
        log.info("请求结果:{}", res);
        client.stop();
    }

    @Test
    public void testSingleArgsCall() throws Exception {
        Discover discover = new ZkDiscover();
        GrpcClient client = new GrpcClient(discover);
        client.start();

        GrpcRequest request = new GrpcRequest();
        request.setServiceName("testService");
        request.setMethodName("hello1");
        request.setVersion("1.0.0");
        request.setArgs(new Object[]{"cheivin"});
        Object res = client.invoke(request);
        log.info("请求结果:{}", res);
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
        log.info("请求结果:{}", res);
        client.stop();
    }

    @Test
    public void testBatchCall() throws Exception {
        Discover discover = new ZkDiscover();
        GrpcClient client = new GrpcClient(discover);
        client.start();

        int count = 1000;
        ExecutorService executor = Executors.newFixedThreadPool(count);
        for (int i = 0; i < count; i++) {
            int finalI = i;
            executor.execute(() -> {
                GrpcRequest request = new GrpcRequest();
                request.setServiceName("testService");
                request.setMethodName("hello1");
                request.setVersion("1.0.0");
                request.setArgs(new Object[]{"cheivin" + finalI});
                try {
                    log.info("请求结果:{}", client.invoke(request));
                } catch (InstanceException | InvokeException e) {
                    e.printStackTrace();
                }
            });
        }
        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.DAYS);
        System.out.println("stop");
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

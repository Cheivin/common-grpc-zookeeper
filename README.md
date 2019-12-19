# common-grpc-zookeeper
common-grpc的zookeeper服务注册发现客户端

# 使用方法

## 定义服务类
```java
import top.cheivin.grpc.annotation.GrpcService;

@GrpcService(service = "testService", alias = "测试", version = "1.0.0", weight = 5)
public class TestService {
    public String hello() {
        System.out.println("hello~~~");
        return "hello~~~";
    }

    public String hello1(String str) {
        System.out.println("hello1~~~" + str);
        return "hello1~~~" + str;
    }

    public String hello2(String str, String user) {
        System.out.println("hello2~~~" + str + "," + user);
        return "hello2~~~" + str + "," + user;
    }
}

```

## 测试方法
```java
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
import top.cheivin.grpc.core.DefaultServiceInfoManage;
import top.cheivin.service.AService;
import top.cheivin.service.TestService;

import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 *
 */
@Slf4j
public class ZkTest {
    private GrpcServer server;

    @Before
    public void startServer() throws Exception {
        // 注册服务
        DefaultServiceInfoManage manage = new DefaultServiceInfoManage();
        manage.addService(AService.class);
        manage.addService(TestService.class);
        // zookeeper服务注册
        Registry client = new ZkRegistry();
        // 服务端
        GrpcServer server = GrpcServer.from(client, manage)
                .port(10000 + new Random().nextInt(20000))
                .build();
        // 启动监听
        server.start();
        this.server = server;
    }

    @After
    public void stopServer() {
        this.server.stop();
    }

    @Test
    public void testMultiArgsCall() throws Exception {
        // zookeeper服务发现
        Discover discover = new ZkDiscover();
        // 客户端
        GrpcClient client = new GrpcClient(discover);
        // 启动服务
        client.start();
        // 定义请求对象
        GrpcRequest request = new GrpcRequest();
        request.setServiceName("testService");
        request.setMethodName("hello2");
        request.setVersion("1.0.0");
        request.setArgs(new Object[]{"hahahaha", "cheivin"});

        // 调用服务
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
```
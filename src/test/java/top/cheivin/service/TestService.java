package top.cheivin.service;

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

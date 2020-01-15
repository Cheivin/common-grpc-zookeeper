package top.cheivin.service;

import top.cheivin.grpc.annotation.GrpcService;

import java.util.ArrayList;
import java.util.List;

@GrpcService(service = "testService", alias = "测试", version = "1.0.0", weight = 5)
public class TestService {
    public String hello() {
        System.out.println("hello~~~unknown");
        return "hello~~~unknown";
    }

    public String hello1(String str) {
        System.out.println("hello1~~~" + str);
        return "hello1~~~" + str;
    }

    public String hello2(String str, String user) {
        System.out.println("hello2~~~" + str + "," + user);
        return "hello2~~~" + str + "," + user;
    }

    public List<String> helloList(String str) {
        List<String> list = new ArrayList<>();
        list.add("hello1," + str);
        list.add("hello2," + str);
        list.add("hello3," + str);
        return list;
    }

    public User helloUser(User user) {
        return user;
    }

    public List<User> helloUserList(User user) {
        List<User> users = new ArrayList<>();
        users.add(user);
        users.add(user);
        users.add(user);
        return users;
    }

    public List<User> helloListUserList(List<User> users) {
        return users;
    }
}

package top.cheivin.service;

import java.util.Date;

/**
 * @author cheivin
 * @date 2020/1/15
 */
public class User {
    private String name;
    private int age;
    private Date loginTime;

    public User() {
    }

    public User(String name, int age, Date loginTime) {
        this.name = name;
        this.age = age;
        this.loginTime = loginTime;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getAge() {
        return age;
    }

    public void setAge(int age) {
        this.age = age;
    }

    public Date getLoginTime() {
        return loginTime;
    }

    public void setLoginTime(Date loginTime) {
        this.loginTime = loginTime;
    }

    @Override
    public String toString() {
        return "User{" +
                "name='" + name + '\'' +
                ", age=" + age +
                ", loginTime=" + loginTime +
                '}';
    }
}

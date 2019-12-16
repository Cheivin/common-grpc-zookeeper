package top.cheivin.service;

import top.cheivin.grpc.annotation.GrpcService;

@GrpcService(service = "testService", alias = "测试", version = "1.1.0")
public class AService {
}

简介
===

Android开发中我们经常需要使用到长连接来维持与服务器端的通讯，Connection提供了长连接的抽象类，以及一个基于netty的默认实现，支持多种通讯协议（String，Json，protobuf）


使用
===
1、IConnectionService是长连接的抽象接口，提供了包括创建长连接、添加监听、发送心跳包等功能
```Java
implementation 'com.yun:connection:1.2.0'
```
```Java

/**
 * 长连接service
 */
public interface IConnectionService {
    /**
     * 创建长连接
     *
     * @param host
     * @param port
     * @param iInitializeData
     */
    public void create(String host, int port, IInitializeData iInitializeData);


    /**
     * 添加监听
     *
     * @param iConnectionListener
     */
    public void addListener(IConnectionListener iConnectionListener);


    /**
     * 移除监听
     *
     * @param iConnectionListener
     */
    public void removeListener(IConnectionListener iConnectionListener);

    /**
     * 开始连接
     */
    public void connect();


    /**
     * 断开连接
     */
    public void disConnect();

    /**
     * 发送数据
     *
     * @param sendMessage
     * @param isNeedReTry 是否需要重连
     */
    public void sendMessage(ISendMessage sendMessage, boolean isNeedReTry);

    /**
     * 是否已连接
     */
    public boolean isConnect();
}

```


2、使用netty的实现类
例:使用protouf作为通讯协议
```Java
IConnectionService iConnectionService = new NettyConnectionService();
IInitializeData iInitializeData = new IInitializeData() {
            @Override
            public ConnectionAgreement getAgreement() {
            //获取协议
                return ConnectionAgreement.PROTOBUF;
            }

            @Override
            public ISendMessage getHeartBeatMessage() {
               //心跳内容
                return new SendMessage(pingMsg);
            }

            @Override
            public ISendMessage getChannelActiveMessage() {
            //注册内容
                return new SendMessage(registerMsg);
            }

            @Override
            public Object agreementMessageData() {
            //协议内容类型
                return Response.getDefaultInstance();
            }
        };
        iConnectionService.create(AppConstant.TCP_ip, AppConstant.TCP_port, iInitializeData);
        iConnectionService.addListener(this);
```

```Java
启动连接服务
iConnectionService.connect();
```

```Java
关闭连接服务
iConnectionService.disConnect();
```








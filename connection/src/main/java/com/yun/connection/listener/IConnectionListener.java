package com.yun.connection.listener;

import com.yun.connection.model.ISendMessage;

import java.lang.reflect.Type;

/**
 * 长连接监听
 */
public interface IConnectionListener<T> {
    /**
     * 连接
     */
    public void onConnect();

    /**
     * 断连
     */
    public void onDisConnect();

    /**
     * 接收到消息
     */
    public Object onReceive(T data);

    /**
     * 重新连接
     */
    public void onReConnect();

    /**
     * 当接收的数据为json时 需要返回
     * 接收的数据类型
     *
     * @return
     */
    public Type getMessageType();


    /**
     * 发送消息
     *
     * @param sendMessage
     */
    public void sendMessage(ISendMessage sendMessage);

}

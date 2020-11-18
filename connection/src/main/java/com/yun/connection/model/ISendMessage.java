package com.yun.connection.model;

/**
 * 发送的数据模型
 */
public interface ISendMessage<T> {
    /**
     * 获取消息
     *
     * @return
     */
    public T getData();
}


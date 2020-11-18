package com.yun.connection.model;

/**
 * 初始化数据
 */
public interface IInitializeData {
    /**
     * 获取协议
     *
     * @return
     */
    public ConnectionAgreement getAgreement();

    /**
     * 心跳内容
     *
     * @return
     */
    public ISendMessage getHeartBeatMessage();

    /**
     * 注册内容
     *
     * @return
     */
    public ISendMessage getChannelActiveMessage();

    /**
     * 协议内容类型
     *
     * @return
     */
    public Object agreementMessageData();

}

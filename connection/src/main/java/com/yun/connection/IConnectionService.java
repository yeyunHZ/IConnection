package com.yun.connection;

import com.yun.connection.listener.IConnectionListener;
import com.yun.connection.model.IInitializeData;
import com.yun.connection.model.ISendMessage;

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

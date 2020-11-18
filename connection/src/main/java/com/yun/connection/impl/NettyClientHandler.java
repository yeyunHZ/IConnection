package com.yun.connection.impl;


import android.util.Log;

import com.google.gson.Gson;
import com.yun.connection.IConnectionService;
import com.yun.connection.listener.IConnectionListener;
import com.yun.connection.model.ConnectionAgreement;
import com.yun.connection.model.IInitializeData;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.concurrent.TimeUnit;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.ReferenceCountUtil;
import io.reactivex.Observable;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;

/**
 * NettyClientHandler
 *
 * @author enyva
 * @version 2017/03/20
 */
public class NettyClientHandler extends SimpleChannelInboundHandler {

    protected static final String TAG = NettyClientHandler.class.getSimpleName();
    private static final Object lock = new Object();
    private static Gson gson = new Gson();
    //是否不断线重连,主动关闭
    private boolean is_force_close = false;
    // Sleep 3 seconds before a reconnection attempt.
    private static final int RECONNECT_DELAY_HANDLER = 5;
    // how many times of not receiving pong message.
    private static final int TIMES_UNRECEIVED_PONG = 10;

    private static final int SIZE_LINKED_QUEUE = 4;


    private volatile int mUnReceivedPongTimes = 0;

    public static LinkedList<String> sChannelActiveQueue = new LinkedList<>();
    public static LinkedList<String> sChannelInActiveQueue = new LinkedList<>();
    private ArrayList<IConnectionListener> connectionListeners;
    private boolean isConnect = false;
    private IConnectionService iConnectionService;
    private IInitializeData iInitializeData;

    public NettyClientHandler(boolean is_force_close, IInitializeData iInitializeData, ArrayList<IConnectionListener> connectionListeners
            , IConnectionService iConnectionService) {
        this.is_force_close = is_force_close;
        this.connectionListeners = connectionListeners;
        this.iConnectionService = iConnectionService;
        this.iInitializeData = iInitializeData;
    }

    public void setIs_force_close(boolean b) {
        this.is_force_close = b;
    }

    //利用写空闲发送心跳检测消息
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        try {
            if (evt instanceof IdleStateEvent) {
                IdleStateEvent e = (IdleStateEvent) evt;
                Log.i("NettyClientHandler", "NettyClientHandler status : " + e.state().name());
                switch (e.state()) {
                    case WRITER_IDLE:
                        Log.d("", "WRITER_IDLE - mUnReceivedPongTimes: " + mUnReceivedPongTimes);
                        break;
                    case READER_IDLE:
                        mUnReceivedPongTimes++;
                        if (mUnReceivedPongTimes > TIMES_UNRECEIVED_PONG) {
                            mUnReceivedPongTimes = 0;
                            Log.d(TAG, "mUnReceivedPongTimes >= TIMES_UNRECEIVED_PONG ,mUnReceivedPongTimes = "
                                    + mUnReceivedPongTimes);
                            ctx.close();
                        } else {
                            if (iInitializeData.getHeartBeatMessage() != null && iInitializeData.getHeartBeatMessage().getData() != null) {
                                if (iInitializeData.getAgreement() == ConnectionAgreement.STRING) {
                                    if (iInitializeData.getHeartBeatMessage().getData() instanceof String) {
                                        ctx.writeAndFlush(((String) iInitializeData.getHeartBeatMessage().getData()));
                                    } else {
                                        ctx.writeAndFlush(gson.toJson(iInitializeData.getHeartBeatMessage().getData()));
                                    }
                                } else if (iInitializeData.getAgreement() == ConnectionAgreement.JSON) {
                                    ctx.writeAndFlush(gson.toJson(iInitializeData.getHeartBeatMessage().getData()));
                                } else if (iInitializeData.getAgreement() == ConnectionAgreement.PROTOBUF) {
                                    ctx.writeAndFlush(iInitializeData.getHeartBeatMessage().getData());
                                }
                                if (connectionListeners != null) {
                                    for (IConnectionListener iConnectionListener : connectionListeners) {
                                        iConnectionListener.sendMessage(iInitializeData.getHeartBeatMessage());
                                    }
                                }
                            }
                        }
                        break;
                    default:
                        break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        try {
            SocketChannel activeSocketChannel = (SocketChannel) ctx.channel();

            Observable.timer(0, TimeUnit.SECONDS).subscribeOn(Schedulers.io()).subscribe(new Consumer<Long>() {
                @Override
                public void accept(Long aLong) throws Exception {
                    if (iInitializeData.getChannelActiveMessage() != null && iInitializeData.getChannelActiveMessage().getData() != null) {
                        boolean isSendSuccess = false;
                        while (!isSendSuccess) {
                            if (iInitializeData.getAgreement() == ConnectionAgreement.STRING) {
                                if (iInitializeData.getChannelActiveMessage().getData() instanceof String) {
                                    isSendSuccess = ctx.writeAndFlush((String) iInitializeData.getChannelActiveMessage().getData()).await(3000);
                                } else {
                                    isSendSuccess = ctx.writeAndFlush(gson.toJson(iInitializeData.getChannelActiveMessage().getData())).await(3000);
                                }
                            } else if (iInitializeData.getAgreement() == ConnectionAgreement.JSON) {
                                isSendSuccess = activeSocketChannel.writeAndFlush(gson.toJson(iInitializeData.getChannelActiveMessage().getData())).await(3000);
                            } else if (iInitializeData.getAgreement() == ConnectionAgreement.PROTOBUF) {
                                isSendSuccess = activeSocketChannel.writeAndFlush(iInitializeData.getChannelActiveMessage().getData()).await(3000);
                            }
                            if (connectionListeners != null) {
                                for (IConnectionListener iConnectionListener : connectionListeners) {
                                    iConnectionListener.sendMessage(iInitializeData.getChannelActiveMessage());
                                }
                            }
                        }

                    }
                }
            });

            synchronized (lock) {
                long currentTimeStamp = System.currentTimeMillis();
                sChannelActiveQueue.add(activeSocketChannel.id().toString() + ":" + activeSocketChannel.isOpen() + ":" + currentTimeStamp);

                for (String channelInfo : sChannelActiveQueue) {
                    Log.d(TAG, "TCP Log - [mChannelActiveQueue] channelInfo = " + channelInfo);
                }
                Log.d(TAG, "TCP Log-------------------------- size = " + sChannelActiveQueue.size());
                if (sChannelActiveQueue.size() > SIZE_LINKED_QUEUE) {
                    Log.d(TAG, "TCP Log remove first channelInfo = " + sChannelActiveQueue.pollFirst());
                }
            }
            ctx.fireChannelActive();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //这里是断线要进行的操作
    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        if (is_force_close) {
            return;
        }
        try {
            super.channelInactive(ctx);
            if (connectionListeners != null) {
                for (IConnectionListener iConnectionListener : connectionListeners) {
                    isConnect = false;
                    iConnectionListener.onDisConnect();
                }
            }
            SocketChannel inActiveSocketChannel = (SocketChannel) ctx.channel();
            Log.i(TAG, "TCP Log: channelInactive 服务端连接中断, local port:" + inActiveSocketChannel.localAddress().getAddress()
                    + ":" + inActiveSocketChannel.localAddress().getPort() + " channelId:" + inActiveSocketChannel.id());
            mUnReceivedPongTimes = 0;
            synchronized (lock) {
                long currentTimeStamp = System.currentTimeMillis();
                String lastChannelInfo = sChannelActiveQueue.peekLast();
                Log.i(TAG, "TCP Log -- active peekLast = " + lastChannelInfo + " , 正常的斷線重連得和channelAactive 的id不同");
                Log.d(TAG, "TCP Log - 重要, 目前active latest channelId  = " + lastChannelInfo.substring(0, lastChannelInfo.indexOf(":")));
                String latestChannelId = lastChannelInfo.substring(0, lastChannelInfo.indexOf(":"));
                sChannelInActiveQueue.add(inActiveSocketChannel.id().toString() + ":" + inActiveSocketChannel.isOpen() + ":" + currentTimeStamp);
                for (String channelInfo : sChannelInActiveQueue) {
                    Log.i(TAG, "TCP Log [sChannelInActiveQueue] channelInfo = " + channelInfo);
                }
                Log.d(TAG, "TCP Log-------------------------- size = " + sChannelInActiveQueue.size());
                if (sChannelInActiveQueue.size() > SIZE_LINKED_QUEUE) {
                    Log.d(TAG, "TCP Log remove first channelInfo = " + sChannelInActiveQueue.pollFirst());
                }
                if (latestChannelId.equals(inActiveSocketChannel.id().toString())) {
                    Log.i(TAG, "TCP Log , channelId 與last channelActive 相同, reconnect");
                    ctx.channel().eventLoop().schedule(() -> {
                        iConnectionService.connect();
                    }, RECONNECT_DELAY_HANDLER, TimeUnit.SECONDS);
                } else {
                    // 如果最新的 channelActive 的channel Id 和 channelInactive callback 回來的channel Id 不同,
                    // 表示有多餘的重複連線了, do not reconnect
                    Log.i(TAG, "TCP Log , =\\=channelId 與last channelActive 不同, normal - disconnected");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //这里是出现异常的话要进行的操作
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        try {
            ctx.close();
            Log.d("TCP Log", "出现异常了。。。。。。。。。。。。。" + cause.toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    /**
     * 销毁
     */
    public void destroy() {
        iConnectionService = null;
    }


    @Override
    protected void messageReceived(ChannelHandlerContext ctx, Object msg) throws Exception {
        try {
            Object returnMsg = null;
            mUnReceivedPongTimes = 0;
            if (connectionListeners != null) {
                for (IConnectionListener iConnectionListener : connectionListeners) {
                    if (!isConnect) {
                        isConnect = true;
                        iConnectionListener.onConnect();
                    }
                    if (iInitializeData.getAgreement() == ConnectionAgreement.STRING) {
                        returnMsg = iConnectionListener.onReceive(msg.toString());
                    } else if (iInitializeData.getAgreement() == ConnectionAgreement.JSON) {
                        returnMsg = iConnectionListener.onReceive(gson.fromJson(msg.toString(), iConnectionListener.getMessageType()));
                    } else if (iInitializeData.getAgreement() == ConnectionAgreement.PROTOBUF) {
                        returnMsg = iConnectionListener.onReceive(msg);
                    }
                    if (returnMsg != null) {
                        break;
                    }
                }
            }
            if (returnMsg != null) {
                if (iInitializeData.getAgreement() == ConnectionAgreement.STRING) {
                    if (returnMsg instanceof String) {
                        ReferenceCountUtil.release(((String) returnMsg));
                    } else {
                        ReferenceCountUtil.release(gson.toJson(returnMsg));
                    }
                } else if (iInitializeData.getAgreement() == ConnectionAgreement.JSON) {
                    ReferenceCountUtil.release(gson.toJson(returnMsg));
                } else if (iInitializeData.getAgreement() == ConnectionAgreement.PROTOBUF) {
                    ReferenceCountUtil.release(returnMsg);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

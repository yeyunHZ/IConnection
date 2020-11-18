package com.yun.connection.impl;

import android.annotation.SuppressLint;

import com.google.gson.Gson;
import com.google.protobuf.MessageLite;
import com.yun.connection.IConnectionService;
import com.yun.connection.listener.IConnectionListener;
import com.yun.connection.model.ConnectionAgreement;
import com.yun.connection.model.IInitializeData;
import com.yun.connection.model.ISendMessage;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.codec.protobuf.ProtobufDecoder;
import io.netty.handler.codec.protobuf.ProtobufEncoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32FrameDecoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32LengthFieldPrepender;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.CharsetUtil;
import io.reactivex.Observable;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;

/**
 * netty长连接
 */
public class NettyConnectionService implements IConnectionService {
    private String host;
    private int port;
    private Bootstrap bootstrap;
    public SocketChannel socketChannel;
    private NettyClientHandler driverNettyClientHandler;
    private static final int RECONNECT_DELAY = 3000;
    private ArrayList<IConnectionListener> connectionListeners;
    private Gson gson;
    private LinkedBlockingQueue<ISendMessage> messageQueue;
    private Thread sendThread;
    private IInitializeData iInitializeData;

    @Override
    public void create(String host, int port, IInitializeData iInitializeData) {
        this.iInitializeData = iInitializeData;
        this.host = host;
        this.port = port;
        connectionListeners = new ArrayList<>();
        createBootstrap(iInitializeData);
        gson = new Gson();
        messageQueue = new LinkedBlockingQueue();
        createSendThread();
    }

    @Override
    public void addListener(IConnectionListener iConnectionListener) {
        connectionListeners.add(iConnectionListener);
    }

    @Override
    public void removeListener(IConnectionListener iConnectionListener) {
        connectionListeners.remove(iConnectionListener);
    }


    @SuppressLint("CheckResult")
    @Override
    public void connect() {
        if (this.socketChannel != null && this.socketChannel.isOpen()) {
            return;
        }
        if (this.socketChannel != null) {
            socketChannel.close();
        }
        if (bootstrap == null) {
            createBootstrap(iInitializeData);
        }
        if (sendThread != null && !sendThread.isAlive()) {
            createSendThread();
        }
        try {
            Observable.timer(3, TimeUnit.SECONDS).subscribeOn(Schedulers.io()).subscribe(new Consumer<Long>() {
                @Override
                public void accept(Long aLong) throws Exception {
                    ChannelFuture future = null;
                    future = bootstrap.connect(new InetSocketAddress(host, port)).sync();
                    if (future.isSuccess()) {
                        socketChannel = (SocketChannel) future.channel();
                    } else {
                        future.channel().closeFuture().sync();
                        if (socketChannel != null) {
                            socketChannel.eventLoop().schedule(() -> {
                                bootstrap.connect();
                            }, RECONNECT_DELAY, TimeUnit.MILLISECONDS);
                        }

                    }
                }
            }, new Consumer<Throwable>() {
                @Override
                public void accept(Throwable throwable) throws Exception {
                    connect();
                }
            });

        } catch (Exception e) {
            Observable.timer(3, TimeUnit.SECONDS).subscribeOn(Schedulers.io()).subscribe(new Consumer<Long>() {
                @Override
                public void accept(Long aLong) throws Exception {
                    connect();
                }
            });
            e.printStackTrace();
        }
    }

    @Override
    public void disConnect() {
        if (socketChannel != null) {
            socketChannel.close();
        }
        if (driverNettyClientHandler != null) {
            driverNettyClientHandler.destroy();
            driverNettyClientHandler = null;
        }
        bootstrap = null;
        sendThread.interrupt();
    }

    @Override
    public void sendMessage(ISendMessage sendMessage, boolean isNeedReTry) {
        if (isNeedReTry) {
            try {
                messageQueue.put(sendMessage);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        } else {
            if (socketChannel != null) {
                if (iInitializeData.getAgreement() == ConnectionAgreement.STRING) {
                    if (sendMessage.getData() instanceof String) {
                        socketChannel.writeAndFlush((String) sendMessage.getData());
                    } else {
                        socketChannel.writeAndFlush(gson.toJson(sendMessage.getData()));
                    }
                } else if (iInitializeData.getAgreement() == ConnectionAgreement.JSON) {
                    socketChannel.writeAndFlush(gson.toJson(sendMessage.getData()));
                } else if (iInitializeData.getAgreement() == ConnectionAgreement.PROTOBUF) {
                    socketChannel.writeAndFlush(sendMessage.getData());
                }
            }
        }
        if (connectionListeners != null) {
            for (IConnectionListener iConnectionListener : connectionListeners) {
                iConnectionListener.sendMessage(sendMessage);
            }
        }
    }

    @Override
    public boolean isConnect() {
        if (socketChannel != null) {
            return socketChannel.isOpen();
        }
        return false;
    }

    private void createBootstrap(IInitializeData iInitializeData) {
        EventLoopGroup eventLoopGroup = new NioEventLoopGroup();
        bootstrap = new Bootstrap();
        bootstrap.channel(NioSocketChannel.class);
        bootstrap.option(ChannelOption.SO_KEEPALIVE, true);
        bootstrap.group(eventLoopGroup);
        bootstrap.remoteAddress(host, port);
        bootstrap.handler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel socketChannel) {
                socketChannel.pipeline().addLast(new IdleStateHandler(3, 3, 0));
                socketChannel.pipeline().addLast("frameDecoder", new LengthFieldBasedFrameDecoder(Integer.MAX_VALUE, 0, 4, 0, 4));
                socketChannel.pipeline().addLast("frameEncoder", new LengthFieldPrepender(4));
                if (iInitializeData.getAgreement() == ConnectionAgreement.PROTOBUF) {
                    socketChannel.pipeline().addLast(new ProtobufVarint32FrameDecoder())
                            .addLast(new ProtobufDecoder((MessageLite) iInitializeData.agreementMessageData()))
                            .addLast(new ProtobufVarint32LengthFieldPrepender())
                            .addLast(new ProtobufEncoder());
                } else {
                    socketChannel.pipeline().addLast("decoder", new StringDecoder(CharsetUtil.UTF_8));
                    socketChannel.pipeline().addLast("encoder", new StringEncoder(CharsetUtil.UTF_8));
                }
                driverNettyClientHandler = new NettyClientHandler(false, iInitializeData, connectionListeners, NettyConnectionService.this);
                socketChannel.pipeline().addLast(driverNettyClientHandler);
            }
        });
    }


    /**
     * 创建发送消息的线程池
     */
    private void createSendThread() {
        if (sendThread != null) {
            sendThread.interrupt();
        }
        sendThread = new Thread(new Runnable() {
            @Override
            public void run() {
                ISendMessage message = null;
                while (true) {
                    message = messageQueue.poll();
                    boolean isSendSuccess = false;
                    try {
                        while (!isSendSuccess && message != null) {
                            if (socketChannel != null) {
                                if (iInitializeData.getAgreement() == ConnectionAgreement.STRING) {
                                    if (message.getData() instanceof String) {
                                        isSendSuccess = socketChannel.writeAndFlush((String) message.getData()).await(3000);
                                    } else {
                                        isSendSuccess = socketChannel.writeAndFlush(gson.toJson(message.getData())).await(3000);
                                    }
                                } else if (iInitializeData.getAgreement() == ConnectionAgreement.JSON) {
                                    isSendSuccess = socketChannel.writeAndFlush(gson.toJson(message.getData())).await(3000);
                                } else if (iInitializeData.getAgreement() == ConnectionAgreement.PROTOBUF) {
                                    isSendSuccess = socketChannel.writeAndFlush(message.getData()).await(3000);
                                }
                            }
                        }
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
        sendThread.start();
    }
}

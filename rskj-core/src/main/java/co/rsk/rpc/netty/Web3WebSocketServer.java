/*
 * This file is part of RskJ
 * Copyright (C) 2018 RSK Labs Ltd.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package co.rsk.rpc.netty;

import co.rsk.config.InternalService;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.timeout.IdleStateHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.net.InetAddress;

public class Web3WebSocketServer implements InternalService {
    private static final Logger logger = LoggerFactory.getLogger(Web3WebSocketServer.class);

    public static final int READER_IDLE_TIME_SECONDS = 5;
    public static final int WRITER_IDLE_TIME_SECONDS = 5;
    public static final int ALL_IDLE_TIME_SECONDS = 5;

    private final InetAddress host;
    private final int port;
    private final RskWebSocketsJsonRpcHandler rskWebSocketsJsonRpcHandler;
    private final JsonRpcWeb3ServerHandler web3ServerHandler;
    private final EventLoopGroup bossGroup;
    private final EventLoopGroup workerGroup;
    private @Nullable ChannelFuture webSocketChannel;

    public Web3WebSocketServer(
            InetAddress host,
            int port,
            RskWebSocketsJsonRpcHandler rskWebSocketsJsonRpcHandler,
            JsonRpcWeb3ServerHandler web3ServerHandler) {
        this.host = host;
        this.port = port;
        this.rskWebSocketsJsonRpcHandler = rskWebSocketsJsonRpcHandler;
        this.web3ServerHandler = web3ServerHandler;
        this.bossGroup = new NioEventLoopGroup();
        this.workerGroup = new NioEventLoopGroup();
    }

    @Override
    public void start() {
        logger.info("RPC WebSocket enabled");
        ServerBootstrap serverBootstrap = new ServerBootstrap();
        serverBootstrap.group(bossGroup, workerGroup)
            .channel(NioServerSocketChannel.class)
                .handler(new LoggingHandler(LogLevel.INFO))
                .childHandler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel ch) throws Exception {
                    ChannelPipeline p = ch.pipeline();
                    p.addLast(new HttpServerCodec());
                    p.addLast(new HttpObjectAggregator(1024 * 1024 * 5));
                    p.addLast(new IdleStateHandler(READER_IDLE_TIME_SECONDS, WRITER_IDLE_TIME_SECONDS, ALL_IDLE_TIME_SECONDS));
                    p.addLast(new WebSocketServerProtocolHandler("/websocket"));
                    p.addLast(rskWebSocketsJsonRpcHandler);
                    p.addLast(web3ServerHandler);
                }
            });
        webSocketChannel = serverBootstrap.bind(host, port);

        try {
            webSocketChannel.sync();
        } catch (InterruptedException e) {
            logger.error("The RPC WebSocket server couldn't be started", e);
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void stop() {
        try {
            webSocketChannel.channel().close().sync();
        } catch (InterruptedException e) {
            logger.error("Couldn't stop the RPC WebSocket server", e);
            Thread.currentThread().interrupt();
        }
        this.bossGroup.shutdownGracefully();
        this.workerGroup.shutdownGracefully();
    }
}

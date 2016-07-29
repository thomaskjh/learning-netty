package com.thomaskjh.delimiter;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.DelimiterBasedFrameDecoder;
import io.netty.handler.codec.string.StringDecoder;

/**
 * Created by jhk on 16/7/29.
 */
public class EchoClient {

    public void run() {
        final EventLoopGroup workGroup = new NioEventLoopGroup();
        try {
            final ByteBuf delimiter = Unpooled.copiedBuffer("||".getBytes());

            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(workGroup)
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) throws Exception {
                            ch.pipeline().addLast(new DelimiterBasedFrameDecoder(1000, delimiter));
                            ch.pipeline().addLast(new StringDecoder());
                            ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                                @Override
                                public void channelActive(ChannelHandlerContext ctx) throws Exception {
                                    ByteBuf hello = Unpooled.copiedBuffer("Hello$$".getBytes());
                                    ctx.writeAndFlush(hello);

                                    ByteBuf world = Unpooled.copiedBuffer("World$$".getBytes());
                                    ctx.writeAndFlush(world);
                                }

                                @Override
                                public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {

                                    String str = (String) msg;
                                    System.out.println(str);

                                }
                            });
                        }
                    })
                    .option(ChannelOption.TCP_NODELAY, true);
            ChannelFuture future = bootstrap.connect("localhost", 32770).sync();
            future.channel().closeFuture().sync();

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            workGroup.shutdownGracefully();
        }
    }

    public static void main(String[] args) {
        EchoClient client = new EchoClient();
        client.run();
    }

}

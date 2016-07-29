# DiscardServer
```java
public class DiscardServerHandler extends ChannelInboundHandlerAdapter {

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {

        try {
            ByteBuf buf = (ByteBuf)msg;
            byte []req = new byte[buf.readableBytes()];
            buf.readBytes(req);
            System.out.println(new String(req, "UTF-8"));
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            ReferenceCountUtil.release(msg);
        }

    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        ctx.close();
    }
}
```
* ChannelInboundHandlerAdapter类实现了ChannelInboundHandler接口，用来处理收到的数据
* `处理器的职责是释放所有传递到该处理器的引用计算对象`，例子中接收的消息类型是ByteBuf，这是一个引用计数对象，必须显示调用release方法来释放

```java
public class DiscardServer {

    public static void main(String[] args) {
        EventLoopGroup bossGroup = new NioEventLoopGroup();
        EventLoopGroup workGroup = new NioEventLoopGroup();

        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) throws Exception {
                            ch.pipeline().addLast(new DiscardServerHandler());
                        }

                    })
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .childOption(ChannelOption.SO_KEEPALIVE, true);

            ChannelFuture future = bootstrap.bind(32770).sync();
            future.channel().closeFuture().sync();

        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            workGroup.shutdownGracefully();
            bossGroup.shutdownGracefully();
        }

    }

}
```
* `NioEventLoopGroup`是用来处理I/O操作的多线程事件循环器，服务器一般有两个，一个用来监听，一个用来处理已接收的连接；
* `ServerBootstrap`是一个启动NIO服务的引导类，调用`bind()`绑定端口，返回`ChannelFuture`对象；
* `ChannelFuture`用来保存netty中IO操作的结果，netty的IO操作都是异步的，因此当ChannelFuture刚刚创建时，异步操作处于未完成状态，通过`isDone()`方法判断当前操作是否完成，`sync()`方法实际上通过wait方法同步等待IO操作完成；
* `ChannelFuture.channel()`返回跟此ChannelFuture相关的IO操作的`Channel`;
* `Channel.closeFuture()`返回一个与关闭IO操作关联的`ChannelFuture`;

# TimeServer
```java
public class TimeServerHandler extends ChannelInboundHandlerAdapter {

    @Override
    public void channelActive(final ChannelHandlerContext ctx) throws Exception {

        String date = new Date().toString();
        byte []bytes = date.getBytes("UTF-8");

        final ByteBuf buf = ctx.alloc().buffer(bytes.length);
        buf.writeBytes(bytes);

        final ChannelFuture f = ctx.writeAndFlush(buf);
        f.addListener(new ChannelFutureListener() {
            public void operationComplete(ChannelFuture future) throws Exception {
                assert  f == future;
                ctx.close();
            }
        });
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        ctx.close();
    }
}
```
* `channelActive`方法会在连接建立时被调用;
* `ChannelHandlerContext.alloc`方法返回当前的`ByteBufAllocator`,用于分配缓冲区空间;
* `ChannelHandlerContext.write`和`ChannelHandlerContext.writeAndFlush`用于向对端写数据,因为IO操作都是异步的,因此返回一个ChannelFuture对象;
* `ChannelFuture.addListener`添加IO操作的监听事件,`ChannelFutureListener.operationComplete`会在IO操作完成后回调,例子中当写完数据后服务器主动关闭连接;

```java
public class TimeClientHandler extends ChannelInboundHandlerAdapter {

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {

        try {
            ByteBuf buf = (ByteBuf)msg;
            byte []bytes = new byte[buf.readableBytes()];
            buf.readBytes(bytes);
            String date = new String(bytes, "UTF-8");
            System.out.println(date);
            ctx.close();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            ReferenceCountUtil.release(msg);
        }
    }
}
```
* 例子中的handler收到数据后打印并关闭连接;

```java
public class TimeClient {

    public void run() {
        EventLoopGroup workGroup = new NioEventLoopGroup();

        try {
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(workGroup)
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) throws Exception {
                            ch.pipeline().addLast(new TimeClientHandler());
                        }
                    })
                    .option(ChannelOption.TCP_NODELAY, true);
            ChannelFuture future = bootstrap.connect("localhost", 32770);
            future.channel().closeFuture().sync();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            workGroup.shutdownGracefully();
        }
    }

    public static void main(String[] args) {
        TimeClient client = new TimeClient();
        client.run();
    }
}
```
* 客户端使用`Bootstrap`作为启动IO服务器的引导类,只需要一个`EventLoopGroup`,用来在连接建立后处理消息;
* 客户端的channel类型是`NioSocketChannel`,服务端的channel类型是`NioServerSocketChannel`;

# LineBasedFrameDecoder
```java
@Override
protected void initChannel(SocketChannel ch) throws Exception {
    ch.pipeline().addLast(new LineBasedFrameDecoder(10));
    ch.pipeline().addLast(new StringDecoder());
    ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            try {
                String m = (String) msg;
                System.out.println(m);

            } finally {
                ReferenceCountUtil.release(msg);
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            if(cause instanceof TooLongFrameException) {
                String msg = "the size of string exceeds 10";
                byte []bytes = msg.getBytes();
                ByteBuf buf = ctx.alloc().buffer(bytes.length);
                // 另一种创建ByteBuf的方式
                // ByteBuf buf = Unpooled.copiedBuffer(msg.getBytes());
                buf.writeBytes(bytes);
                ctx.writeAndFlush(buf);
            } else {
                throw new Exception(cause);
            }
        }
    });
}
```
* `LineBasedFrameDecoder`将接收到的ByteBuf以`\n`或`\r\n`作为结束标识进行解码,构造函数的参数表示最大行的长度,超出则抛异常;该异常可以被后续的ChannelHandler捕获;
* `StringDecoder`将ByteBuf解码为String,供后续的ChannelHandler使用;

```java
// server
@Override
protected void initChannel(SocketChannel ch) throws Exception {
    ch.pipeline().addLast(new DelimiterBasedFrameDecoder(1000, delimiter));
    ch.pipeline().addLast(new StringDecoder());
    ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            try {

                String str = (String) msg;
                str += "||";
                ByteBuf buf = Unpooled.copiedBuffer(str.getBytes());
                ctx.writeAndFlush(buf);

            } finally {
                ReferenceCountUtil.release(msg);
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            ctx.close();
        }
    });
}

// client
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

```
* `DelimiterBasedFrameDecoder`指定分隔符的解码器;

# FixedLengthFrameDecoder
* `FixedLengthFrameDecoder`固定长度的解码器;






package github.javaguide.remoting.transport.netty.server;

import github.javaguide.config.CustomShutdownHook;
import github.javaguide.config.RpcServiceConfig;
import github.javaguide.factory.SingletonFactory;
import github.javaguide.provider.ServiceProvider;
import github.javaguide.provider.impl.ZkServiceProviderImpl;
import github.javaguide.remoting.transport.netty.codec.RpcMessageDecoder;
import github.javaguide.remoting.transport.netty.codec.RpcMessageEncoder;
import github.javaguide.utils.RuntimeUtil;
import github.javaguide.utils.concurrent.threadpool.ThreadPoolFactoryUtil;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.util.concurrent.TimeUnit;

/**
 * Server. Receive the client message, call the corresponding method according to the client message,
 * and then return the result to the client.
 * 基于Netty网络通信的RPC服务端
 *
 * @author shuang.kou
 * @createTime 2020年05月25日 16:42:00
 */
@Slf4j
@Component
public class NettyRpcServer {

    public static final int PORT = 9998;

    //RPC服务的生产者类的单例
    private final ServiceProvider serviceProvider = SingletonFactory.getInstance(ZkServiceProviderImpl.class);

    public void registerService(RpcServiceConfig rpcServiceConfig) {
        serviceProvider.publishService(rpcServiceConfig);
    }

    /**
     * 1.创建java的钩子方法在JVM销毁前释放资源（zookeeper节点和线程池）
     * 2.给引导类配置两大线程组，确定了多线程模式（默认线程数=cpu核心数*2）（一个 Acceptor 线程只负责监听客户端的连接，一个 NIO 线程池负责具体处理）
     * 3.TCP开启Nagle 算法
     * 4.开启 TCP 底层心跳机制
     * 5.设置支持长连接TCP连接的数量
     * 6.给引导类创建一个ChannelInitializer ，然后指定了服务端消息的业务处理逻辑 HelloServerHandler 对象
     * 7.绑定本机服务器和指定端口（9998），同步等待绑定成功
     * 8.等待服务端监听端口关闭（阻塞等待直到服务器Channel关闭(closeFuture()方法获取Channel 的CloseFuture对象,然后调用sync()方法)）
     * @param
     * @return: void
     * @author: gefeng
     * @date: 2022/8/31 17:29
     */
    @SneakyThrows
    public void start() {
        CustomShutdownHook.getCustomShutdownHook().clearAll();
        String host = InetAddress.getLocalHost().getHostAddress();
        EventLoopGroup bossGroup = new NioEventLoopGroup(1);//接收连接的线程
        EventLoopGroup workerGroup = new NioEventLoopGroup();//用于具体处理
        DefaultEventExecutorGroup serviceHandlerGroup = new DefaultEventExecutorGroup(
                RuntimeUtil.cpus() * 2,
                ThreadPoolFactoryUtil.createThreadFactory("service-handler-group", false)
        );
        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                    //服务端网络操作的抽象类：NioServerSocketChannel（NIO模式）
                    .channel(NioServerSocketChannel.class)
                    // TCP默认开启了 Nagle 算法，该算法的作用是尽可能的发送大数据快，减少网络传输。TCP_NODELAY 参数的作用就是控制是否启用 Nagle 算法。
                    .childOption(ChannelOption.TCP_NODELAY, true)
                    // 是否开启 TCP 底层心跳机制
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    //表示系统用于临时存放已完成三次握手的请求的队列的最大长度,如果连接建立频繁，服务器处理创建新连接较慢，可以适当调大这个参数（支持长连接TCP连接的数量）
                    .option(ChannelOption.SO_BACKLOG, 128)
                    //打印日志
                    .handler(new LoggingHandler(LogLevel.INFO))
                    // 当客户端第一次进行请求的时候才会进行初始化
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        /**
                         * 1.当Channel被创建时，它会被自动地分配到它专属的ChannelPipeLine
                         * 2.ChannelPipeLine为ChannelHandler的链，一个pipeline上可以有多个ChannelHandler
                         * 3.在 ChannelPipeline 上通过 addLast() 方法添加一个或者多个ChannelHandler （一个数据或者事件可能会被多个 Handler 处理） 。
                         * 当一个 ChannelHandler 处理完之后就将数据交给下一个 ChannelHandler 。（Channel中的数据将被这些ChannelHandler一次处理）
                         * @param ch 1
                         * @return: void
                         * @author: gefeng
                         * @date: 2022/8/31 19:02
                         */
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            // 30 秒之内没有收到客户端请求的话就关闭连接
                            ChannelPipeline p = ch.pipeline();
                            p.addLast(new IdleStateHandler(30, 0, 0, TimeUnit.SECONDS));
                            p.addLast(new RpcMessageEncoder());//消息序列化编码器
                            p.addLast(new RpcMessageDecoder());//消息序列化解码器
                            p.addLast(serviceHandlerGroup, new NettyRpcServerHandler());
                        }
                    });

            // 绑定端口，同步等待绑定成功
            ChannelFuture f = b.bind(host, PORT).sync();
            // 等待服务端监听端口关闭
            f.channel().closeFuture().sync();
        } catch (InterruptedException e) {
            log.error("occur exception when start server:", e);
        } finally {
            log.error("shutdown bossGroup and workerGroup");
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
            serviceHandlerGroup.shutdownGracefully();
        }
    }


}

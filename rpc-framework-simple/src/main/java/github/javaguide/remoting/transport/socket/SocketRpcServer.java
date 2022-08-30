package github.javaguide.remoting.transport.socket;

import github.javaguide.config.CustomShutdownHook;
import github.javaguide.config.RpcServiceConfig;
import github.javaguide.factory.SingletonFactory;
import github.javaguide.provider.ServiceProvider;
import github.javaguide.provider.impl.ZkServiceProviderImpl;
import github.javaguide.utils.concurrent.threadpool.ThreadPoolFactoryUtil;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;

import static github.javaguide.remoting.transport.netty.server.NettyRpcServer.PORT;

/**
 * RPC服务端
 * @author shuang.kou
 * @createTime 2020年05月10日 08:01:00
 */
@Slf4j
public class SocketRpcServer {

    private final ExecutorService threadPool;//线程池
    private final ServiceProvider serviceProvider;


    public SocketRpcServer() {
        threadPool = ThreadPoolFactoryUtil.createCustomThreadPoolIfAbsent("socket-server-rpc-pool");//线程池名字以socket-server-rpc-pool为前缀
        serviceProvider = SingletonFactory.getInstance(ZkServiceProviderImpl.class);//ZkServiceProviderImpl类的单例对象（RPC服务的生产者实例）
    }

    /**
     * 注册rpcServiceConfig中配置的服务
     * @param rpcServiceConfig 1
     * @return: void
     * @author: gefeng
     * @date: 2022/8/30 20:29
     */
    public void registerService(RpcServiceConfig rpcServiceConfig) {
        serviceProvider.publishService(rpcServiceConfig);
    }

    /**
     * 1.创建服务端Socket实例，绑定本机ip和NettyRpcServer指定端口（9998）
     * 2. todo CustomShutdownHook.getCustomShutdownHook().clearAll();
     * 3.将接收到的socket请求消息封装成任务放入线程池处理
     * 4.
     * @param
     * @return: void
     * @author: gefeng
     * @date: 2022/8/30 20:35
     */
    public void start() {
        try (ServerSocket server = new ServerSocket()) {
            String host = InetAddress.getLocalHost().getHostAddress();
            server.bind(new InetSocketAddress(host, PORT));
            CustomShutdownHook.getCustomShutdownHook().clearAll();
            Socket socket;
            while ((socket = server.accept()) != null) {
                log.info("client connected [{}]", socket.getInetAddress());
                threadPool.execute(new SocketRpcRequestHandlerRunnable(socket));
            }
            threadPool.shutdown();
        } catch (IOException e) {
            log.error("occur IOException:", e);
        }
    }

}

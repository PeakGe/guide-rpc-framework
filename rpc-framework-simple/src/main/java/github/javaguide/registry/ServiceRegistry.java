package github.javaguide.registry;

import github.javaguide.extension.SPI;

import java.net.InetSocketAddress;

/**
 * service registration
 * 加了SPI注解的才可以使用ExtensionLoader加载
 * @author shuang.kou
 * @createTime 2020年05月13日 08:39:00
 */
@SPI
public interface ServiceRegistry {
    /**
     * register service
     *
     * @param rpcServiceName    rpc service name
     * @param inetSocketAddress service address（对ip和端口的封装，用于Socket通信）
     */
    void registerService(String rpcServiceName, InetSocketAddress inetSocketAddress);

}

package github.javaguide.provider.impl;

import github.javaguide.config.RpcServiceConfig;
import github.javaguide.enums.RpcErrorMessageEnum;
import github.javaguide.exception.RpcException;
import github.javaguide.extension.ExtensionLoader;
import github.javaguide.provider.ServiceProvider;
import github.javaguide.registry.ServiceRegistry;
import github.javaguide.remoting.transport.netty.server.NettyRpcServer;
import lombok.extern.slf4j.Slf4j;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RPC服务的生产者：添加、获取指定名、发布RPC服务
 * @author shuang.kou
 * @createTime 2020年05月13日 11:23:00
 */
@Slf4j
public class ZkServiceProviderImpl implements ServiceProvider {

    /**
     * key: rpc service name(interface name + version + group)
     * value: service object
     */
    private final Map<String, Object> serviceMap;//已注册的<rpc服务名，rpc服务>集合
    private final Set<String> registeredService;//已注册的rpc服务名集合
    private final ServiceRegistry serviceRegistry;//

    public ZkServiceProviderImpl() {
        serviceMap = new ConcurrentHashMap<>();
        registeredService = ConcurrentHashMap.newKeySet();
        // 获取ServiceRegistry.class的一个实例（尝试从cachedInstances中获取）
        // 之前已创建过，则直接从缓存中获取；没有创建过则先加载该类（根据缩写名zk尝试从cachedClasses中获取）
        // 没有加载过则使用ExtensionLoader类的加载器加载ServiceRegistry.class为名的文件中所有列出的类，后放入<Class缩写名-Class对象>cachedClasses缓存中
        // 加载META-INF/extensions/目录下文件名为ServiceRegistry.class的文件中所有列出的类，并放入cachedClasses
        // 加载完成后，新建实例并放入cachedInstances中
        // 注：使用的加载器=ExtensionLoader类的加载器
        serviceRegistry = ExtensionLoader.getExtensionLoader(ServiceRegistry.class).getExtension("zk");
    }

    /**
     * 将rpcServiceConfig中RPC服务加入serviceMap和registeredService
     * @param rpcServiceConfig 1
     * @return: void
     * @author: gefeng
     * @date: 2022/8/30 20:03
     */
    @Override
    public void addService(RpcServiceConfig rpcServiceConfig) {
        String rpcServiceName = rpcServiceConfig.getRpcServiceName();
        if (registeredService.contains(rpcServiceName)) {
            return;
        }
        registeredService.add(rpcServiceName);
        serviceMap.put(rpcServiceName, rpcServiceConfig.getService());
        log.info("Add service: {} and interfaces:{}", rpcServiceName, rpcServiceConfig.getService().getClass().getInterfaces());
    }

    /**
     * 从serviceMap中获取指定RPC服务名的服务
     * @param rpcServiceName 1
     * @return: java.lang.Object
     * @author: gefeng
     * @date: 2022/8/30 20:04
     */
    @Override
    public Object getService(String rpcServiceName) {
        Object service = serviceMap.get(rpcServiceName);
        if (null == service) {
            throw new RpcException(RpcErrorMessageEnum.SERVICE_CAN_NOT_BE_FOUND);
        }
        return service;
    }

    /**
     * 发布服务
     * 1.创建1个InetSocketAddress对象封装ip和NettyRpcServer端口（9998）用于通信
     * 2.添加服务
     * 3.在zookeeper中创建该服务对应节点，名字包含了RPC服务名和地址（ip+端口）
     * @param rpcServiceConfig 1
     * @return: void
     * @author: gefeng
     * @date: 2022/8/30 20:22
     */
    @Override
    public void publishService(RpcServiceConfig rpcServiceConfig) {
        try {
            String host = InetAddress.getLocalHost().getHostAddress();
            this.addService(rpcServiceConfig);
            serviceRegistry.registerService(rpcServiceConfig.getRpcServiceName(), new InetSocketAddress(host, NettyRpcServer.PORT));
        } catch (UnknownHostException e) {
            log.error("occur exception when getHostAddress", e);
        }
    }

}

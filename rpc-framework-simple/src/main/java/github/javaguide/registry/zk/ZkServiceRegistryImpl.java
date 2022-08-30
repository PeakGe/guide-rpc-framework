package github.javaguide.registry.zk;

import github.javaguide.registry.ServiceRegistry;
import github.javaguide.registry.zk.util.CuratorUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.curator.framework.CuratorFramework;

import java.net.InetSocketAddress;

/**
 * service registration  based on zookeeper
 * 提供RPC服务注册功能
 * @author shuang.kou
 * @createTime 2020年05月31日 10:56:00
 */
@Slf4j
public class ZkServiceRegistryImpl implements ServiceRegistry {

    /**
     * 利用zookeeper注册服务（节点包含了名字和服务地址）
     * 1.在/my-rpc节点下新增持久节点表示该服务
     * 2.节点名=RPC服务名+/ip:端口
     * @param rpcServiceName rpc服务名
     * @param inetSocketAddress 2
     * @return: void
     * @author: gefeng
     * @date: 2022/7/28 16:37
     */
    @Override
    public void registerService(String rpcServiceName, InetSocketAddress inetSocketAddress) {
        String servicePath = CuratorUtils.ZK_REGISTER_ROOT_PATH + "/" + rpcServiceName + inetSocketAddress.toString();
        CuratorFramework zkClient = CuratorUtils.getZkClient();
        CuratorUtils.createPersistentNode(zkClient, servicePath);
    }
}

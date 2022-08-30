package github.javaguide.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * @author shuang.kou
 * @createTime 2020年07月21日 20:23:00
 **/
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
@ToString
public class RpcServiceConfig {
    /**
     * service version
     */
    private String version = "";
    /**
     * when the interface has multiple implementation classes, distinguish by group
     */
    private String group = "";

    /**
     * target service
     */
    private Object service;

    /**
     * 获取RPC服务名字=服务Class对象第一个接口名字+组名+版本名
     * @param
     * @return: java.lang.String
     * @author: gefeng
     * @date: 2022/8/30 20:01
     */
    public String getRpcServiceName() {
        return this.getServiceName() + this.getGroup() + this.getVersion();
    }

    /**
     * 获取服务的名字=服务Class对象实现的第一个接口的全名字
     * @param
     * @return: java.lang.String
     * @author: gefeng
     * @date: 2022/8/30 19:57
     */
    public String getServiceName() {
        return this.service.getClass().getInterfaces()[0].getCanonicalName();
    }
}

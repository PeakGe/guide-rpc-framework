package github.javaguide.extension;

/**
 * 用于封装各种类型，volatile保证多线程修改时的内存可见性
 * @return:
 * @author: gefeng
 * @date: 2023/2/7 15:11
 */
public class Holder<T> {

    private volatile T value;

    public T get() {
        return value;
    }

    public void set(T value) {
        this.value = value;
    }
}

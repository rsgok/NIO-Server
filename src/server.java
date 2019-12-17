import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

class IMServer implements Runnable {
    static int PORT = 7000;
    Selector selector;
    Set<SocketChannel> clientChannels = new HashSet<SocketChannel>();

    public IMServer() throws Exception {
        // 创建selector
        selector = Selector.open();
        // 创建server channel
        ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.configureBlocking(false); // 设置为非阻塞模式
        serverSocketChannel.socket().bind(new InetSocketAddress(PORT));
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
        System.out.println("IM server端启动");
    }

    public static void main(String[] args) {
        try {
            IMServer IM = new IMServer();
            new Thread(IM).start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        while (true) {
            int count = 0;
            try {
                // 记录就绪的channel数
                count = selector.select(1000);
            } catch (IOException e) {
                e.printStackTrace();
            }
            if (count > 0) {
                // 获取就绪的channel
                Set<SelectionKey> selectionKeySet = selector.selectedKeys();
                Iterator<SelectionKey> keyIterator = selectionKeySet.iterator();
                SelectionKey selectionKey;
                // 遍历每一个selectionKey做相应的处理
                while (keyIterator.hasNext()) {
                    selectionKey = keyIterator.next();
                    this.handleSelectionKey(selectionKey);
                    // 移除SelectionKey对象
                    // 当下次channel处于就绪，Selector仍然会吧这些key再次加入进来。
                    keyIterator.remove();
                }
            }
        }
    }

    // 处理selectionKey
    void handleSelectionKey(SelectionKey key) {
        if (!key.isValid()) return;
        if (key.isAcceptable()) {
            // a connection was accepted by a ServerSocketChannel
            this.handleAcceptableKey(key);
        } else if (key.isReadable()) {
            // a channel is ready for reading
            this.handleReadableKey(key);
        } else {
            System.err.println("condition we don't handle");
        }
    }

    void handleReadableKey(SelectionKey key) {
        // 取得对应的channel
        SocketChannel socketChannel = (SocketChannel) key.channel();
        // 建立buffer，从channel中读取数据
        ByteBuffer readBuffer = ByteBuffer.allocate(1024); // max byte num: 1024
        int readBytes;
        try {
            readBytes = socketChannel.read(readBuffer);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        // 发送消息
        if (readBytes > 0) {
            readBuffer.flip();
            byte[] bytes = new byte[readBuffer.remaining()];
            readBuffer.get(bytes);
            String msg = new String(bytes, StandardCharsets.UTF_8);
            // 向所有客户端（包括发送方）转发
            this.sendMsg(msg);
        } else if (readBytes < 0) {
            // 客户端断开
            // 取消此键的通道到其选择器的注册
            key.cancel();
            try {
                // 关闭该通道
                socketChannel.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            // 从客户端列表中移除
            clientChannels.remove(socketChannel);
            System.out.println("client num: " + clientChannels.size());
        }
    }

    // 接收到socket connect请求
    void handleAcceptableKey(SelectionKey key) {
        // 获取server channel
        ServerSocketChannel serverSocketChannel = (ServerSocketChannel) key.channel();
        // 待用的client channel
        SocketChannel socketChannel = null;
        try {
            socketChannel = serverSocketChannel.accept();
            socketChannel.configureBlocking(false);
            socketChannel.register(selector, SelectionKey.OP_READ);
        } catch (IOException e) {
            // 删除key
            // 取消此键的通道到其选择器的注册
            key.cancel();
            try {
                // 关闭该通道
                socketChannel.close();
            } catch (IOException e1) {
                e1.printStackTrace();
            }
            e.printStackTrace();
        } finally {
            if (socketChannel != null) {
                clientChannels.add(socketChannel);
                System.out.println("client num: " + clientChannels.size());
            }
        }
    }

    // 发送消息
    void sendMsg(String msg) {
        byte[] bytes = msg.getBytes();
        System.out.println("sending client num: " + clientChannels.size());
        for (SocketChannel channel : clientChannels) {
            ByteBuffer writeBuffer = ByteBuffer.allocate(bytes.length);
            writeBuffer.put(bytes);
            writeBuffer.flip();
            try {
                // 将buffer中的数据写入channel
                channel.write(writeBuffer);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}

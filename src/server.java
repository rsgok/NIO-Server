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
                Set<SelectionKey> selectionKeySet = selector.selectedKeys();
                Iterator<SelectionKey> keyIterator = selectionKeySet.iterator();
                SelectionKey selectionKey;
                // 遍历每一个selectionKey做相应的处理
                while (keyIterator.hasNext()) {
                    selectionKey = keyIterator.next();
                    this.handleSelectionKey(selectionKey);
                    keyIterator.remove();
                }
            }
        }
    }

    void handleSelectionKey(SelectionKey key) {
        if (!key.isValid()) return;
        if (key.isAcceptable()) {
            this.handleAcceptableKey(key);
        } else if (key.isReadable()) {
            this.handleReadableKey(key);
        } else {
            System.err.println("wrong");
        }
    }

    void handleReadableKey(SelectionKey key) {
        SocketChannel socketChannel = (SocketChannel) key.channel();
        ByteBuffer readBuffer = ByteBuffer.allocate(1024);
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
            key.cancel();
            try {
                socketChannel.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            clientChannels.remove(socketChannel);
            System.out.println("client num: " + clientChannels.size());
        }
    }

    // 接受到socket请求
    void handleAcceptableKey(SelectionKey key) {
        ServerSocketChannel serverSocketChannel = (ServerSocketChannel) key.channel();
        SocketChannel socketChannel = null;
        try {
            socketChannel = serverSocketChannel.accept();
            socketChannel.configureBlocking(false);
            socketChannel.register(selector, SelectionKey.OP_READ);
        } catch (IOException e) {
            // 删除key
            this.closeSelectionKey(key);
            if (socketChannel != null) {
                try {
                    socketChannel.close();
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
            }
            e.printStackTrace();
        }
        if (socketChannel != null) {
            clientChannels.add(socketChannel);
            System.out.println("client num: " + clientChannels.size());
        }
    }

    void sendMsg(String msg) {
        byte[] bytes = msg.getBytes();
        System.out.println("sending client num: " + clientChannels.size());
        int i = 0;
        for (SocketChannel channel : clientChannels) {
            i++;
            System.out.println("send msg to: " + i);
            ByteBuffer writeBuffer = ByteBuffer.allocate(bytes.length);
            writeBuffer.put(bytes);
            writeBuffer.flip();
            try {
                channel.write(writeBuffer);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    void closeSelectionKey(SelectionKey key) {
        key.cancel();
        if (key.channel() != null) {
            try {
                key.channel().close();
            } catch (IOException e1) {
                e1.printStackTrace();
            }
        }
    }
}

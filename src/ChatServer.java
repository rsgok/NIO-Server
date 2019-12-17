import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.text.SimpleDateFormat;
import java.util.Date;

public class ChatServer implements Runnable {

    static int PORT = 9999;
    Selector selector;
    SelectionKey serverKey;
    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public ChatServer() {
        init();
    }

    public void init() {
        try {
            selector = Selector.open();
            ServerSocketChannel serverChannel = ServerSocketChannel.open();
            serverChannel.configureBlocking(false);
            serverChannel.socket().bind(new InetSocketAddress(PORT));
            serverKey = serverChannel.register(selector, SelectionKey.OP_ACCEPT);
            printInfo("server starting.......");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        try {
            while (true) {
                if (selector.select() > 0) {
                    for(SelectionKey key : selector.selectedKeys())
                    {
                        if (key.isAcceptable()) {
                            System.out.println(key.toString() + " : 接收");
                            selector.selectedKeys().remove(key);
                            ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel();
                            SocketChannel socket = serverChannel.accept();
                            socket.configureBlocking(false);
                            socket.register(selector, SelectionKey.OP_READ);
                        }
                        if (key.isValid() && key.isReadable()) {
                            System.out.println(key.toString() + " : 读");
                            readMsg(key);
                        }
                        if (key.isValid() && key.isWritable()) {
                            System.out.println(key.toString() + " : 写");
                            writeMsg(key);
                        }
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    void readMsg(SelectionKey key) {
        SocketChannel channel = null;
        try {
            channel = (SocketChannel) key.channel();
            //设置buffer缓冲区
            ByteBuffer buffer = ByteBuffer.allocate(1024);
            //假如客户端关闭了通道，这里在对该通道read数据，会发生IOException，捕获到Exception后，关闭掉该channel，取消掉该key
            int count = channel.read(buffer);
            StringBuffer buf = new StringBuffer();
            //如果读取到了数据
            if (count > 0) {
                //让buffer翻转，把buffer中的数据读取出来
                buffer.flip();
                buf.append(new String(buffer.array(), 0, count));
            }
            String msg = buf.toString();

            if (msg.startsWith("login")) {
                for(SelectionKey clientKey : selector.selectedKeys())
                {
                    if (clientKey != serverKey) {
                        clientKey.interestOps(clientKey.interestOps() | SelectionKey.OP_WRITE);
                    }
                }
            } else if (msg.startsWith("exit")) {
                selector.selectedKeys().remove(key);
            } else {
                printInfo(msg);
                for(SelectionKey clientKey : selector.selectedKeys())
                {
                    if (clientKey != serverKey) {
                        clientKey.attach(msg);
                        clientKey.interestOps(clientKey.interestOps() | SelectionKey.OP_WRITE);
                    }
                }
            }
            buffer.clear();
        } catch (IOException e) {
            key.cancel();
            try {
                channel.socket().close();
                channel.close();
            } catch (IOException e1) {
                e1.printStackTrace();
            }
        }
    }

    void writeMsg(SelectionKey key) {
        try {
            SocketChannel channel = (SocketChannel) key.channel();
            Object attachment = key.attachment();
            key.attach("");
            channel.write(ByteBuffer.wrap(attachment.toString().getBytes()));
            key.interestOps(SelectionKey.OP_READ);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    void printInfo(String str) {
        System.out.println("[" + sdf.format(new Date()) + "] -> " + str);
    }
}
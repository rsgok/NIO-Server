class main {
    public static void main(String[] args) {
        ChatServer server = new ChatServer();
        new Thread(server).start();
    }
}
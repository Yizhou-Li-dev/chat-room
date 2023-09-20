package server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Class Chatroom server
 */
public class ChatroomServer {
  private ServerSocket serverSocket;
  private final AtomicInteger clientCount = new AtomicInteger(0);
  private static final int LIMIT_CLIENT_NUM = 10;

  /**
   * constructor of chatroom server
   * @param serverSocket serverSocket that listen on connections
   */
  public ChatroomServer(ServerSocket serverSocket, int port){
    this.serverSocket = serverSocket;
    System.out.println("The server is started, listening on port " + port);
  }

  /**
   * start thread when there is connection and keep count on the client count.
   */
  public void startServer(){
    try {
      while (!serverSocket.isClosed()) {
        Socket socket = serverSocket.accept();
        ClientHandler clientHandler = new ClientHandler(socket, clientCount);
        if (clientCount.incrementAndGet() < LIMIT_CLIENT_NUM) {
          Thread thread = new Thread(clientHandler);
          thread.start();
        } else {
          clientCount.decrementAndGet();
          clientHandler.exceedClose();
          System.out.println("Client number limit exceeded!");
        }
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  /**
   * main entry of the chatroom server class
   * @param args args
   * @throws IOException io exception
   */
  public static void main(String[] args) throws IOException {
    if (args.length < 1) {
      System.out.println("Not enough arguments. You need to supply port number");
      System.exit(0);
    }

    int port = 0;
    try {
      port = Integer.parseInt(args[0]);
    } catch (NumberFormatException e) {
      System.out.println("Invalid port number");
      System.exit(0);
    }

    ServerSocket serverSocket = new ServerSocket(port);
    ChatroomServer chatroomServer = new ChatroomServer(serverSocket, port);
    chatroomServer.startServer();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ChatroomServer that = (ChatroomServer) o;
    return serverSocket.equals(that.serverSocket);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public int hashCode() {
    return Objects.hash(serverSocket);
  }
}



package client;

import util.MessageIdentifier;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Random;
import java.util.Scanner;

/**
 * The client side app, which handles client's sending message and receiving
 * message
 */
public class ChatroomClient {
  private String username;
  private final Socket socket;
  private final DataInputStream in;
  private final DataOutputStream out;
  private final Scanner scanner = new Scanner(System.in);

  private boolean shouldPlay = true;

  /**
   *
   * The constructor of this client
   * 
   * @param socket the socket to connect to the right testServer
   * @throws IOException IOException
   */
  public ChatroomClient(Socket socket) throws IOException {
    this.socket = socket;
    this.in = new DataInputStream(socket.getInputStream());
    this.out = new DataOutputStream(socket.getOutputStream());
  }

  /**
   * Client's way of interacting with the terminal
   *
   */
  public void sendMessages() {
    System.out.println(
        "Welcome, the followings are instructions of using this program. Press ? to repeat the program help :)\n");
    System.out.println("""
        • logoff: sends a DISCONNECT_MESSAGE to the Server. Usage: loggoff
        • who: sends a QUERY_CONNECTED_USERS to the Server. Usage: who
        • @user: sends a DIRECT_MESSAGE to the specified user. Usage: @user <user> <message>
        • @all: sends a BROADCAST_MESSAGE to all users connected. Usage: @all <message>
        • !user: sends a SEND_INSULT message to the specified user. Usage: !user <user>
        • games: enter the game mode to play some games with others. Usage: games
        """);
    while (!socket.isClosed()) {
      try {
        String input = scanner.nextLine();
        String[] strArr;

        if (input.equals("?")) {
          System.out.println("""
        • logoff: sends a DISCONNECT_MESSAGE to the Server. Usage: loggoff
        • who: sends a QUERY_CONNECTED_USERS to the Server. Usage: who
        • @user: sends a DIRECT_MESSAGE to the specified user. Usage: @user <user> <message>
        • @all: sends a BROADCAST_MESSAGE to all users connected. Usage: @all <message>
        • !user: sends a SEND_INSULT message to the specified user. Usage: !user <user>
        • games: enter the game mode to play some games with others. Usage: games
        """);
        } else if (input.equals("logoff")) {
          out.writeInt(MessageIdentifier.DISCONNECT_MESSAGE);
          out.writeChar(' ');
          out.writeInt(username.length());
          out.writeChar(' ');
          out.write(username.getBytes(StandardCharsets.UTF_8));
        } else if (input.equals("who")) {
          out.writeInt(MessageIdentifier.QUERY_CONNECTED_USERS);
          out.writeChar(' ');
          out.writeInt(username.length());
          out.writeChar(' ');
          out.write(username.getBytes(StandardCharsets.UTF_8));
        } else if (input.startsWith("@user")) {
          strArr = input.split(" ");

          if (strArr.length < 3) {
            System.out.println("@user's arguments has to be at least 3");
            continue;
          }

          String content = input.substring(strArr[0].length() + strArr[1].length() + 2);
          out.writeInt(MessageIdentifier.DIRECT_MESSAGE);
          out.writeChar(' ');
          out.writeInt(username.length());
          out.writeChar(' ');
          out.write(username.getBytes(StandardCharsets.UTF_8));
          out.writeChar(' ');
          out.writeInt(strArr[1].length());
          out.writeChar(' ');
          out.write(strArr[1].getBytes(StandardCharsets.UTF_8));
          out.writeChar(' ');
          out.writeInt(content.length());
          out.writeChar(' ');
          out.write(content.getBytes(StandardCharsets.UTF_8));
        } else if (input.startsWith("@all")) {
          strArr = input.split(" ");
          System.out.println(input);

          if (strArr.length < 2) {
            System.out.println("@all's arguments has to be at least 2");
            continue;
          }

          String content = input.substring(strArr[0].length() + 1);
          out.writeInt(MessageIdentifier.BROADCAST_MESSAGE);
          out.writeChar(' ');
          out.writeInt(username.length());
          out.writeChar(' ');
          out.write(username.getBytes(StandardCharsets.UTF_8));
          out.writeChar(' ');
          out.writeInt(content.length());
          out.writeChar(' ');
          out.write(content.getBytes(StandardCharsets.UTF_8));
        } else if (input.startsWith("!user")) {
          strArr = input.split(" ");

          if (strArr.length < 2) {
            System.out.println("@all's arguments has to be at least 2");
            continue;
          }

          out.writeInt(MessageIdentifier.SEND_INSULT);
          out.writeChar(' ');
          out.writeInt(username.length());
          out.writeChar(' ');
          out.write(username.getBytes(StandardCharsets.UTF_8));
          out.writeChar(' ');
          out.writeInt(strArr[1].length());
          out.writeChar(' ');
          out.write(strArr[1].getBytes(StandardCharsets.UTF_8));
        } else if (input.equals("games")) {
          System.out.println(
              """
                  Here are some games we can play in this server:
                  1.add numbers
                    rule: this game is played with the whole server. In this game, you either
                          randomly roll a number or select a number in range 0-100. Who first
                          add the number beyond 100, who lost. Everybody else wins.

                  Enter the game number to play
                  """);
          String gameChoice = scanner.nextLine();
          if (gameChoice.equals("1")) {
            shouldPlay = true;
            System.out.println("Enter a number or ran to randomly roll a number");
            while (shouldPlay) {
              // select the number
              String numChoice = scanner.nextLine();
              int num = 0;

              if (!shouldPlay)
                break;

              if (numChoice.equals("ran")) {
                num = new Random().nextInt(101);
              } else {
                try {
                  num = Integer.parseInt(numChoice);
                } catch (NumberFormatException e) {
                  System.out.println("Invalid number!!!");
                  continue;
                }
              }

              // broadcast the result to the server
              out.writeInt(MessageIdentifier.PLAY_ADD_NUMBER_SEND);
              out.writeChar(' ');
              out.writeInt(username.length());
              out.writeChar(' ');
              out.write(username.getBytes(StandardCharsets.UTF_8));
              out.writeChar(' ');
              out.writeInt(num);
            }
          } else {
            System.out.println("Sorry, the game does not exist");
          }
        } else {
          System.out.println("Invalid command, please enter a valid one");
        }
      } catch (IOException e) {
        System.out.println("I/O exception occured. Connection is off.");
      }
    }
  }

  /**
   * Listen to messages from the Server
   */
  public void listenToMessages() {
    new Thread(() -> {
      while (!socket.isClosed()) {
        try {
          var messageType = in.readInt();
          if (messageType == MessageIdentifier.CONNECT_RESPONSE) { // connect response
            in.readChar();
            var isSuccessful = in.readBoolean();
            in.readChar();
            var messageLength = in.readInt();
            in.readChar();
            var bytes = in.readNBytes(messageLength);
            if (isSuccessful) {
              closeEverything();
              System.exit(0);
            } else {
              System.out.println(new String(bytes, StandardCharsets.UTF_8));
            }
          } else if (messageType == MessageIdentifier.QUERY_USER_RESPONSE) { // query users response
            in.readChar();
            var length = in.readInt();
            if (length != 0) {
              for (int i = 0; i < length; i++) {
                in.readChar();
                var messageLength = in.readInt();
                in.readChar();
                var bytes = in.readNBytes(messageLength);
                System.out.println(new String(bytes, StandardCharsets.UTF_8));
              }
            } else {
              System.out.println("The request may fails, or there are no other users connected.");
            }
          } else if (messageType == MessageIdentifier.BROADCAST_MESSAGE) { // broadcast message and insults
            in.readChar();
            var messageLength = in.readInt();
            in.readChar();
            var senderUsername = in.readNBytes(messageLength);
            in.readChar();
            messageLength = in.readInt();
            in.readChar();
            byte[] bytes = in.readNBytes(messageLength);
            System.out.println(
                new String(senderUsername, StandardCharsets.UTF_8) + ": " + new String(bytes, StandardCharsets.UTF_8));
          } else if (messageType == MessageIdentifier.DIRECT_MESSAGE) { // direct message
            in.readChar();
            var length = in.readInt();
            in.readChar();
            byte[] bytes = in.readNBytes(length);
            var senderUsername = new String(bytes, StandardCharsets.UTF_8);
            in.readChar();
            length = in.readInt();
            in.readChar();
            in.readNBytes(length);
            in.readChar();
            length = in.readInt();
            in.readChar();
            bytes = in.readNBytes(length);
            System.out.println(senderUsername + ": " + new String(bytes, StandardCharsets.UTF_8));
          } else if (messageType == MessageIdentifier.FAILED_MESSAGE) { // failed message
            in.readChar();
            var messageLength = in.readInt();
            in.readChar();
            var bytes = in.readNBytes(messageLength);
            System.out.println(new String(bytes, StandardCharsets.UTF_8));
          } else if (messageType == MessageIdentifier.PLAY_ADD_NUMBER_RECEIVE) { // receive the message from play add
                                                                                 // number game
            in.readChar();
            int isLose = in.readInt();
            // System.out.println("isLose" + isLose);
            if (isLose == 1) {
              // System.out.println("!!!!!!!!!!!!!!!!!!!!!");
              shouldPlay = false;
            }
          }
        } catch (IOException e) {
          System.out.println("connection is off in listen to message");
          closeEverything();
          System.exit(0);
        }
      }
    }).start();
  }

  /**
   * Start the client's script by this function
   */
  public void startClient() {
    initConnection();
    listenToMessages();
    sendMessages();
  }

  /**
   * Initalize the client to server connection and send the username to server
   */
  private void initConnection() {
    while (!socket.isClosed()) {
      try {
        System.out.println("Please enter your name:");
        this.username = scanner.nextLine();

        out.writeInt(MessageIdentifier.CONNECT_MESSAGE);
        out.writeChar(' ');
        out.writeInt(username.length());
        out.writeChar(' ');
        out.write(username.getBytes(StandardCharsets.UTF_8));

        in.readInt();
        in.readChar();
        var isSuccessful = in.readBoolean();
        in.readChar();
        var messageLength = in.readInt();
        in.readChar();
        var bytes = in.readNBytes(messageLength);
        System.out.println(new String(bytes, StandardCharsets.UTF_8));
        if (isSuccessful) {
          break;
        }
      } catch (IOException e) {
        System.out.println("I/O exception occured. Connection is off.");
      }
    }
  }

  /**
   * disconnect, and close all resources
   * 
   * @throws IOException
   */
  private void closeEverything() {
    try {
      if (this.in != null) {
        in.close();
      }
      if (this.out != null) {
        out.close();
      }
      if (this.socket != null) {
        socket.close();
      }
      scanner.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  /**
   * The main function of client that build connection with the testServer
   * 
   * @param args arguments
   * @throws IOException IOException
   */
  public static void main(String[] args) throws IOException {
    if (args.length < 2) {
      System.out.println("Not enough arguments. You need to supply ip address and socket.");
      System.exit(0);
    }

    try {
      Integer.parseInt(args[1]);
    } catch (NumberFormatException e) {
      System.out.println("Invalid socket number");
      System.exit(0);
    }

    ChatroomClient chatroomClient = new ChatroomClient(new Socket(args[0], Integer.parseInt(args[1])));
    chatroomClient.startClient();
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
    ChatroomClient chatroomClient = (ChatroomClient) o;
    return Objects.equals(username, chatroomClient.username) && Objects.equals(socket,
        chatroomClient.socket) && Objects.equals(in, chatroomClient.in)
        && Objects.equals(out,
            chatroomClient.out);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public int hashCode() {
    return Objects.hash(username, socket, in, out);
  }
}

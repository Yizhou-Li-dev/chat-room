package server;

import java.util.Objects;
import util.MessageIdentifier;
import assignment4.problem1.DirtyWordsShooter;
import assignment4.problem1.JsonHandler;
import assignment4.problem1.SyntaxTree;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import util.Protocol;

/**
 * Class ClientHandler that handles one client connection
 */
public class ClientHandler implements Runnable, Protocol {
  private static final Map<String, ClientHandler> clientHandlers = new HashMap<>();
  private final Socket socket;
  private final AtomicInteger clientCount; // only function decrementAndGet() should be called when this client is
                                           // closed.
  private String username;

  private DataOutputStream out;
  private DataInputStream in;

  private static int playAddNumberScore = 0;

  /**
   * constructor of ClientHandler
   * 
   * @param socket      the socket between client and server
   * @param clientCount the atomic interger of number of client
   * @throws IOException io exception
   */
  public ClientHandler(Socket socket, AtomicInteger clientCount) throws IOException {
    this.socket = socket;
    this.clientCount = clientCount;

    try {
      this.out = new DataOutputStream(this.socket.getOutputStream());
      this.in = new DataInputStream(this.socket.getInputStream());
    } catch (IOException e) {
      closeEverything();
      System.out.println("Connection error!");
    }
  }

  /**
   * process input when the socket is connected.
   */
  @Override
  public void run() {
    while (socket.isConnected()) {
      try {
        processInput();
      } catch (IOException e) {
        try {
          if (clientHandlers.containsKey(username)) {
            clientHandlers.remove(username);
            clientCount.decrementAndGet();
          }
          closeEverything();
        } catch (IOException ex) {
          throw new RuntimeException(ex);
        }
        System.out.println(this.username + " is off");
        break;
      }
    }
  }

  /**
   * when the connect number is exceed, send a fail msg and close everything.
   * 
   * @throws IOException IOException
   */
  public void exceedClose() throws IOException {
    writeConnectResponse(false, "The connect client number is exceeded, please try again later.");
    closeEverything();
  }

  /**
   * close the streams and socket
   * 
   * @throws IOException io exception
   */
  private void closeEverything() throws IOException {
    if (this.in != null) {
      in.close();
    }
    if (this.out != null) {
      out.close();
    }
    if (this.socket != null) {
      socket.close();
    }
  }

  /**
   * process all the input from the client
   * 
   * @throws IOException io exception
   */
  @Override
  public void processInput() throws IOException {
    int messageType = in.readInt();
    in.readChar();
    if (username == null && messageType == MessageIdentifier.CONNECT_MESSAGE) {
      int usernameSize = in.readInt();
      in.readChar();
      this.username = new String(in.readNBytes(usernameSize), StandardCharsets.UTF_8);
      if (clientHandlers.containsKey(username)) {
        writeConnectResponse(false, "Username is already exist, please choose another one.");
        username = null;
      } else {
        clientHandlers.put(username, this);
        writeConnectResponse(true, "There are " + (clientCount.get() - 1) + " other connected clients.");
        writeBroadcastMessage(this.username, "Now join in the chatroom.");
      }
    } else if (username != null) {
      processRequest(messageType);
    } else {
      writeFailMsg("Can't process other request when username is unset.");
    }
  }

  /**
   * process all the normal request from the client
   * 
   * @param messageType the message identifier
   * @throws IOException io exception
   */
  private void processRequest(int messageType) throws IOException {
    int usernameSize;
    String username;
    switch (messageType) {
      case MessageIdentifier.CONNECT_MESSAGE:
        usernameSize = in.readInt();
        in.readChar();
        username = new String(in.readNBytes(usernameSize), StandardCharsets.UTF_8);
        writeConnectResponse(false, "You've already connect as " + username + ". Please start the conversation.");
        break;

      case MessageIdentifier.QUERY_CONNECTED_USERS:
        usernameSize = in.readInt();
        in.readChar();
        username = new String(in.readNBytes(usernameSize), StandardCharsets.UTF_8);
        if (username.equals(this.username)) {
          writeQueryResponse(clientHandlers.keySet().toArray(new String[0]));
        } else {
          writeFailMsg("The username of the QUERY CONNECTED USERS request doesn't match.");
        }
        break;

      case MessageIdentifier.BROADCAST_MESSAGE:
        usernameSize = in.readInt();
        in.readChar();
        username = new String(in.readNBytes(usernameSize), StandardCharsets.UTF_8);
        in.readChar();
        int broadcastMsgSize = in.readInt();
        in.readChar();
        String broadcastMsg = new String(in.readNBytes(broadcastMsgSize), StandardCharsets.UTF_8);
        if (username.equals(this.username)) {
          writeBroadcastMessage(username, broadcastMsg);
        } else {
          writeFailMsg("The username from the BROADCAST MESSAGE request doesn't match.");
        }
        break;

      case MessageIdentifier.DIRECT_MESSAGE:
        int senderUsernameSize = in.readInt();
        in.readChar();
        String senderUsername = new String(in.readNBytes(senderUsernameSize), StandardCharsets.UTF_8);
        in.readChar();
        int recipientUsernameSize = in.readInt();
        in.readChar();
        String recipientUsername = new String(in.readNBytes(recipientUsernameSize), StandardCharsets.UTF_8);
        in.readChar();
        int directMsgSize = in.readInt();
        in.readChar();
        String directMsg = new String(in.readNBytes(directMsgSize), StandardCharsets.UTF_8);

        if (!senderUsername.equals(this.username)) {
          writeFailMsg("The sender username from the DIRECT MESSAGE request doesn't match.");
        } else if (!clientHandlers.containsKey(recipientUsername)) {
          writeFailMsg("The recipient username from the DIRECT MESSAGE request doesn't exist.");
        } else {
          writeDirectMessage(senderUsername, recipientUsername, directMsg);
        }
        break;

      case MessageIdentifier.DISCONNECT_MESSAGE:
        usernameSize = in.readInt();
        in.readChar();
        username = new String(in.readNBytes(usernameSize), StandardCharsets.UTF_8);

        if (username.equals(this.username)) {
          writeConnectResponse(true, "You are no longer connected.");
          clientHandlers.remove(this.username);
          clientCount.decrementAndGet();
          closeEverything();
          writeBroadcastMessage(this.username, "Now leave the chatroom.");

        } else {
          writeConnectResponse(false, "The username from the DISCONNECT MESSAGE request doesn't match");
        }
        break;

      case MessageIdentifier.SEND_INSULT:
        senderUsernameSize = in.readInt();
        in.readChar();
        senderUsername = new String(in.readNBytes(senderUsernameSize), StandardCharsets.UTF_8);
        in.readChar();
        recipientUsernameSize = in.readInt();
        in.readChar();
        recipientUsername = new String(in.readNBytes(recipientUsernameSize), StandardCharsets.UTF_8);

        if (!senderUsername.equals(this.username)) {
          writeFailMsg("The sender username from the DIRECT MESSAGE request doesn't match.");
        } else if (!clientHandlers.containsKey(recipientUsername)) {
          writeFailMsg("The recipient username from the DIRECT MESSAGE request doesn't exist.");
        } else {
          writeInsult(senderUsername, recipientUsername);
        }
        break;

      case MessageIdentifier.PLAY_ADD_NUMBER_SEND:
        senderUsernameSize = in.readInt();
        in.readChar();
        senderUsername = new String(in.readNBytes(senderUsernameSize), StandardCharsets.UTF_8);
        in.readChar();
        int num = in.readInt();
        playAddNumberScore += num;

        System.out.println(senderUsername);
        System.out.println(playAddNumberScore);

        if (playAddNumberScore > 100) {
          // game over
          writeBroadcastMessage("server",
              "game over. " + senderUsername + " lose. Final score is: " + playAddNumberScore);
          out.writeInt(MessageIdentifier.PLAY_ADD_NUMBER_RECEIVE);
          out.writeChar(' ');
          out.writeInt(1);
          playAddNumberScore = 0;
        } else {
          // game continues
          writeBroadcastMessage("server",
              "game continues. " + senderUsername + " added a score. The score now is: " + playAddNumberScore);
          out.writeInt(MessageIdentifier.PLAY_ADD_NUMBER_RECEIVE);
          out.writeChar(' ');
          out.writeInt(0);
        }
        break;

      default:
        throw new RuntimeException("invalid message type.");
    }
  }

  /**
   * write the connection response to the outputStream.
   * 
   * @param success boolean value about success
   * @param msg     message
   * @throws IOException io exception
   */
  private void writeConnectResponse(boolean success, String msg) throws IOException {
    out.writeInt(MessageIdentifier.CONNECT_RESPONSE);
    out.writeChar(' ');
    out.writeBoolean(success);
    out.writeChar(' ');
    out.writeInt(msg.length());
    out.writeChar(' ');
    out.write(msg.getBytes(StandardCharsets.UTF_8));
  }

  /**
   * write query response to the outputStream
   * 
   * @param usernames array of username strings
   * @throws IOException io exception
   */
  private void writeQueryResponse(String[] usernames) throws IOException {
    out.writeInt(MessageIdentifier.QUERY_USER_RESPONSE);
    out.writeChar(' ');
    out.writeInt(usernames.length - 1);
    for (String username : usernames) {
      if (!username.equals(this.username)) {
        out.writeChar(' ');
        out.writeInt(username.length());
        out.writeChar(' ');
        out.write(username.getBytes(StandardCharsets.UTF_8));
      }
    }
  }

  /**
   * write Broadcast message to all the connected client
   * 
   * @param senderUsername sender's username
   * @param msg            message
   * @throws IOException io exception
   */
  private void writeBroadcastMessage(String senderUsername, String msg) throws IOException {
    for (ClientHandler c : clientHandlers.values()) {
      c.out.writeInt(MessageIdentifier.BROADCAST_MESSAGE);
      c.out.writeChar(' ');
      c.out.writeInt(senderUsername.length());
      c.out.writeChar(' ');
      c.out.write(senderUsername.getBytes(StandardCharsets.UTF_8));
      c.out.writeChar(' ');
      c.out.writeInt(msg.length());
      c.out.writeChar(' ');
      c.out.write(msg.getBytes(StandardCharsets.UTF_8));
    }
  }

  /**
   * write direct message to the specific client.
   * 
   * @param senderUsername    sender username
   * @param recipientUsername recipient username
   * @param msg               message
   * @throws IOException io exception
   */
  private void writeDirectMessage(String senderUsername, String recipientUsername, String msg) throws IOException {
    var recipientOut = clientHandlers.get(recipientUsername).out;
    recipientOut.writeInt(MessageIdentifier.DIRECT_MESSAGE);
    recipientOut.writeChar(' ');
    recipientOut.writeInt(senderUsername.length());
    recipientOut.writeChar(' ');
    recipientOut.write(senderUsername.getBytes(StandardCharsets.UTF_8));
    recipientOut.writeChar(' ');
    recipientOut.writeInt(recipientUsername.length());
    recipientOut.writeChar(' ');
    recipientOut.write(recipientUsername.getBytes(StandardCharsets.UTF_8));
    recipientOut.writeChar(' ');
    recipientOut.writeInt(msg.length());
    recipientOut.writeChar(' ');
    recipientOut.write(msg.getBytes(StandardCharsets.UTF_8));
  }

  /**
   * generate and broadcast insult.
   * 
   * @param senderUsername    sender username
   * @param recipientUsername recipient username
   * @throws IOException io exception
   */
  private void writeInsult(String senderUsername, String recipientUsername) throws IOException {
    JsonHandler jsonHandler = new JsonHandler(Path.of("./src/main/resources/insult_grammar.json"));
    try {
      DirtyWordsShooter dirtyWordsShooter = new DirtyWordsShooter(new SyntaxTree(jsonHandler.parseGrammar()));
      var insult = dirtyWordsShooter.shootDirtyWords() + "    --to " + recipientUsername;
      writeBroadcastMessage(senderUsername, insult);
    } catch (ClassNotFoundException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * write failed msg to client
   * 
   * @param msg message
   * @throws IOException io exception
   */
  private void writeFailMsg(String msg) throws IOException {
    out.writeInt(MessageIdentifier.FAILED_MESSAGE);
    out.writeChar(' ');
    out.writeInt(msg.length());
    out.writeChar(' ');
    out.write(msg.getBytes(StandardCharsets.UTF_8));
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
    ClientHandler that = (ClientHandler) o;
    return Objects.equals(username, that.username);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public int hashCode() {
    return Objects.hash(username);
  }
}

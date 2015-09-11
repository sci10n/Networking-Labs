import ChatApp.*;                                      // The package containing our stubs
import org.omg.CosNaming.*;                            // HelloClient will use the naming service.
import org.omg.CosNaming.NamingContextPackage.*;
import org.omg.CORBA.*;                                // All CORBA applications need these classes.
import org.omg.PortableServer.*;   
import org.omg.PortableServer.POA;
import java.io.*;
import javax.swing.*;
import javax.swing.event.*;
import java.awt.event.*;
import java.awt.Font;

class ChatCallbackImpl extends ChatCallbackPOA{
  private ORB orb;
  public void setORB(ORB orb_val) {
   orb = orb_val;
 }
  private JFrame frame;
  private JTextArea myArea;
  public ChatCallbackImpl(JFrame frame, JTextArea area){
    this.frame = frame;
    this.myArea = area;
  }

  public void callback(String notification){
    System.out.println(notification);
  }

  public void joinGame(){
    System.out.println("Joining game");
    frame.setVisible(true);
  }

  public void leaveGame(){
    frame.setVisible(false);
  }

  public void drawGame(String map){
    myArea.setText(map);
  }
}

public class ChatClient
{
  public static Chat chatImpl;
  public static void main(String args[]){
    JFrame frame = new JFrame("Game");
    JTextArea myArea = new JTextArea();
    JTextField inputArea = new JTextField();
    myArea.setFont(new Font("monospaced", Font.PLAIN, 15));
    BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
    try {
      // create and initialize the ORB
      ORB orb = ORB.init(args, null);

      // create servant (impl) and register it with the ORB
      ChatCallbackImpl chatCallbackImpl = new ChatCallbackImpl(frame, myArea);
      chatCallbackImpl.setORB(orb);

      // get reference to RootPOA and activate the POAManager
      POA rootpoa = POAHelper.narrow(orb.resolve_initial_references("RootPOA"));
      rootpoa.the_POAManager().activate();

      // get the root naming context 
      org.omg.CORBA.Object objRef = orb.resolve_initial_references("NameService");
      NamingContextExt ncRef = NamingContextExtHelper.narrow(objRef);

      // resolve the object reference in naming
      String name = "Chat";
      chatImpl = ChatHelper.narrow(ncRef.resolve_str(name));

      // obtain callback reference for registration w/ server
      org.omg.CORBA.Object ref = rootpoa.servant_to_reference(chatCallbackImpl);
      ChatCallback cref = ChatCallbackHelper.narrow(ref);


      // Application code goes below
      String chat = chatImpl.say(cref, "\n  Hello.... chatImpl");

      //Create and set up the window
      myArea.setEditable(false);
      JScrollPane scrollPane = 
        new JScrollPane(myArea,
          JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
          JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);

      final JTextField input = inputArea;
      final ChatCallback creference = cref;
      inputArea.addActionListener(new ActionListener(){
        public void actionPerformed(ActionEvent evt) {
          String text = input.getText();
          ChatClient.chatImpl.play(creference,text);
          input.setText("");
        }
      });

      frame.getContentPane().setLayout(new BoxLayout(frame.getContentPane(), BoxLayout.Y_AXIS));
      frame.getContentPane().add(scrollPane);
      frame.getContentPane().add(inputArea);
      frame.pack();

      while(true){
        String commandString = in.readLine();
        if (commandString.length() == 0)
          continue;
        chatImpl.say(cref, commandString); 
        if(commandString.equals("quit")){
           break;
        }
      }
      in.close();
    } catch(Exception e) {
      System.out.println("ERROR: " + e);
      e.printStackTrace(System.out);
      try{
        in.close();
      }catch(Exception e2){
        System.out.println("ERROR WHEN CLOSING INPUT STREAM");
        e2.printStackTrace(System.out);
      }
    }

  
  }
}

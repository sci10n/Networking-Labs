import ChatApp.*;                                      // The package containing our stubs. 
import org.omg.CosNaming.*;                            // HelloServer will use the naming service. 
import org.omg.CosNaming.NamingContextPackage.*;       // ..for exceptions. 
import org.omg.CORBA.*;                                // All CORBA applications need these classes. 
import org.omg.PortableServer.*;   
import org.omg.PortableServer.POA;
import java.util.*;
import javax.swing.*;

class ChatImpl extends ChatPOA
{
  private ORB orb;
  private HashMap<ChatCallback, String> users;
  private HashMap<ChatCallback,Character> players;
  private char[][] gameBoard = new char[8][8];
  private int emptyTiles = 8 * 8;
  private int placedX = 0;
  private int placedO= 0;
  private boolean gameRunning = false;

  public void setORB(ORB orb_val) {
    orb = orb_val;
    users = new HashMap<ChatCallback, String>();
  }
    
    private void setupOthello(ChatCallback callobj){
	if(!gameRunning){
            players = new HashMap<ChatCallback, Character>();
            emptyTiles = gameBoard.length * gameBoard[0].length;
            placedX = 0;
            placedO = 0;
	    for(int x = 0; x < gameBoard.length; x++)
		for(int y = 0; y < gameBoard[0].length; y++)
		    if(x != 1 || y != 1){
			gameBoard[x][y] = 'o';
			placedO++;
			emptyTiles--;
		    }
		    else
			gameBoard[x][y] = ' ';
            gameRunning = true;

            for(ChatCallback i : users.keySet()){
		if(!users.get(callobj).equals(""))
		    i.callback("Server:\t " + users.get(callobj) + " is setting up a new game");
		else
		    i.callback("Server:\tNew Game started");
	    }
	}
    }
  public String say(ChatCallback callobj, String msg)
  {
    if(!users.containsKey(callobj))
      users.put(callobj, "");
    String[] str = msg.split(" ");

    if(str[0].equals("join")){   //JOIN
      if(!users.containsValue(str[1])){
        users.put(callobj,str[1]);
        for(ChatCallback i : users.keySet()){
          i.callback("Server:\tWelcome " + users.get(callobj));
        }	    
      }
      else{
        callobj.callback("Error: user"+ str[1] + " is already an active chatter");
      }
    }
    else if(str[0].equals("quit")){ //QUIT
      callobj.callback("Server:\t quit");
      orb.shutdown(false);
    }
    else if(str[0].equals("leave")){  //LEAVE
      String name = users.get(callobj);

      if(!name.equals("")){
        callobj.callback("Bye "+ name);
        for(ChatCallback i : users.keySet()){
          if(!i.equals(callobj))
            i.callback("Server:\t" + users.get(callobj) + " left");
        }	    
        users.put(callobj,"");
      }
      else{
        callobj.callback("Error: Not registerd");
      }
    }
    else if(str[0].equals("post")){ //POST
      if(!users.get(callobj).equals("")){
        for(ChatCallback i : users.keySet()){
          i.callback("" + users.get(callobj) + " said:" + msg.substring(str[0].length()));
        }	    
      }
    }
    else if(str[0].equals("list")){ //LIST
        callobj.callback("List of registered users");
      for(String s: users.values())
        if(!s.equals(""))
       callobj.callback("Connected: " + s);
     }
    else if(str[0].equals("othello") && str.length == 2){
	if(str[1].equals("o") || str[1].equals("x")){
	    setupOthello(callobj);
	    players.put(callobj,str[1].charAt(0));
	    callobj.drawGame(compileBoard());
	    callobj.joinGame();
	}
	else{
	    callobj.callback("Not a proper team");
	}
    }
    return ("         ....Goodbye!\n");
  }
  private String compileBoard(){
   String tmp = "   [0][1][2][3][4][5][6][7]\n" ;
   for(int y = 0; y < gameBoard[0].length; y++){
     tmp += "["+ ((char)('a' + y)) + "]";
     for(int x = 0; x < gameBoard.length; x++)
       tmp += "[" +gameBoard[x][y] + "]";
     tmp +="\n";
   }
   return tmp;
 }

  private String compileWin(char team){
    String tmp = "=====[TEAM " + team +" WON!]=====\n";
    tmp+= "With " + (team == 'x' ? placedX : placedO) + " placed markers\n";
    return tmp;
  }

  public String play(ChatCallback callobj, String msg){
    String[] str = msg.split(" ");
    try{
      if(!gameRunning){
        callobj.leaveGame();
      }
      int x = Integer.parseInt(str[0]);
      int y = str[1].charAt(0) - 'a';

      if(x >= 0 && x < gameBoard.length)
        if(y >= 0 && y < gameBoard[0].length)
          if(gameBoard[x][y] == ' '){
            gameBoard[x][y] = players.get(callobj);
            if(players.get(callobj) == 'o')
              placedO++;
            else
              placedX++;
            emptyTiles--;
            if(emptyTiles == 0){
              for(ChatCallback i : users.keySet()){
                i.drawGame(compileWin(placedX > placedO ? 'x' : 'o'));
              }
              gameRunning = false;
            }
            else
              for(ChatCallback i : users.keySet()){
                i.drawGame(compileBoard());
              }
          }
          else 
            System.out.println("NOPE");

    }catch(Exception e){
      System.out.println("NOPE");
    }
    return("               ...Playing\n");
  }
}

public class ChatServer 
{
  public static void main(String args[]) 
  {
    try { 
	    // create and initialize the ORB
      ORB orb = ORB.init(args, null); 

      // create servant (impl) and register it with the ORB
      ChatImpl chatImpl = new ChatImpl();
      chatImpl.setORB(orb); 

      // get reference to rootpoa & activate the POAManager
      POA rootpoa = 
      POAHelper.narrow(orb.resolve_initial_references("RootPOA"));  
      rootpoa.the_POAManager().activate(); 

      // get the root naming context
      org.omg.CORBA.Object objRef = 
      orb.resolve_initial_references("NameService");
      NamingContextExt ncRef = NamingContextExtHelper.narrow(objRef);

      // obtain object reference from the servant (impl)
      org.omg.CORBA.Object ref = 
      rootpoa.servant_to_reference(chatImpl);
      Chat cref = ChatHelper.narrow(ref);

      // bind the object reference in naming
      String name = "Chat";
      NameComponent path[] = ncRef.to_name(name);
      ncRef.rebind(path, cref);

      // Application code goes below
      System.out.println("ChatServer ready and waiting ...");
      
      // wait for invocations from clients
      orb.run();
    }
    catch(Exception e) {
      System.err.println("ERROR : " + e);
      e.printStackTrace(System.out);
    }

    System.out.println("ChatServer Exiting ...");
  }

}

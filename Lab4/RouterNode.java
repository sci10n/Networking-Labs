import javax.swing.*;        
import java.util.*;

public class RouterNode {
  //true = enable/false = disable poison reverse
  private static final boolean poison = true;
  private int currentRouterID;
  private GuiTextArea myGUI;
  private RouterSimulator sim;
  //cost to each not 0 if same, INFINITY if not known
  private int[] costs = new int[RouterSimulator.NUM_NODES];
  private int[] origCosts = new int[RouterSimulator.NUM_NODES];
  private boolean[] connected = new boolean[RouterSimulator.NUM_NODES];

  //--------------------------------------------------
  public RouterNode(int ID, RouterSimulator sim, int[] costs) {
    currentRouterID = ID;
    this.sim = sim;
    myGUI = new GuiTextArea("  Output window for Router #"+ ID + "  ");

    System.arraycopy(costs, 0, this.costs, 0, RouterSimulator.NUM_NODES);
    System.arraycopy(costs, 0, origCosts, 0, RouterSimulator.NUM_NODES);

    for(int i = 0; i < RouterSimulator.NUM_NODES; i++)
      if(costs[i] != RouterSimulator.INFINITY && i != currentRouterID)
        connected[i] = true;
    //sendToConnected(costs);

    for(int i = 0; i < RouterSimulator.NUM_NODES; i++)
      if(connected[i])
        sendUpdate( new RouterPacket(currentRouterID, i, costs));
  }

  /*private void sendToConnected(int [] costs)
  {
    for(int i = 0; i < RouterSimulator.NUM_NODES; i++)
      if(connected[i])
        sendUpdate( new RouterPacket(currentRouterID, i, costs));
  }*/

  //--------------------------------------------------
  //routes keeps the first node for each path
  int[] routes = new int[RouterSimulator.NUM_NODES];

  public void recvUpdate(RouterPacket pkt)
  {
    int[] sendcost = null;
    int[] sendcostsP = null;
    boolean dirtyflag = false;

    //Update table with new info
    for(int i = 0; i < pkt.mincost.length; i++)
    {
      int cost = costs[pkt.sourceid] + pkt.mincost[i];

      if(cost < costs[i])
      {
        costs[i] = cost;
        routes[i] = pkt.sourceid;
        sendcost = costs;
        dirtyflag = true;
      }
      else if(routes[i] == pkt.sourceid && cost > costs[i] && poison)
      {
        if(connected[i])
          routes[i] = i;

        costs[i] = origCosts[i];
        int[] tmp = new int[RouterSimulator.NUM_NODES];
        System.arraycopy(costs,0,tmp,0,RouterSimulator.NUM_NODES);
        tmp[pkt.sourceid] = RouterSimulator.INFINITY;
        sendcostsP = tmp;
        sendcost = costs;
        dirtyflag = true;
      }
    }
    if(dirtyflag)
      for(int i = 0; i < RouterSimulator.NUM_NODES; i++)
        if(connected[i] && sendcostsP != null && i == pkt.sourceid)
          sendUpdate( new RouterPacket(currentRouterID, i, sendcostsP));
        else if(connected[i])
          sendUpdate( new RouterPacket(currentRouterID, i, sendcost));
  }


  private void sendUpdate(RouterPacket pkt)
  {
    sim.toLayer2(pkt);
  }


  //--------------------------------------------------
  public void printDistanceTable()
  {
    myGUI.println("Current table for " + currentRouterID +
                  "  at time " + sim.getClocktime());
    myGUI.println(" costs " + Arrays.toString(costs));
  }

  //--------------------------------------------------
  public void updateLinkCost(int dest, int newcost)
  {
    //Helpfunction to change edge cost from this node to dest node
    //Will use the cost probably or call the simulation.
    costs[dest] = newcost;
    origCosts[dest] = newcost;
    connected[dest] = newcost == RouterSimulator.INFINITY ? false : true;
    for(int i = 0; i< RouterSimulator.NUM_NODES; i++)
      if(connected[i])
        sendUpdate( new RouterPacket(currentRouterID, i, costs));
  }
}

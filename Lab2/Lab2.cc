//Proxy
//Group mares480@student.liu.se, erijo599@student.liu.se
#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <errno.h>
#include <string.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <netdb.h>
#include <arpa/inet.h>
#include <sys/wait.h>
#include <signal.h>
#include <iostream>
#include <sstream>
#include <algorithm>
#include <vector>
#include <string>
#include <list>
#include <utility>

#define BACKLOG 10

using namespace std;



//Check if data is plaintext
bool isText(string str);

//Send data to a socket
int sendall(int socket, list<string> &buffer, int flags);

//handles the response from the server, if not in plaintext it get sent emidiatly to browser
bool server_response(int socket, int browser, list<string> &res, int flags);

//handles requests from the browser
void browser_request(int socket, char *&res, int &resSize , int flags);

//handles the modificatin of the neccecary header tags
char *modify_header(char *res, int &resSize);

//checks if a linked list of strings contains anny of the keywords
bool contains_keywords(list<string> str, vector<string> keywords);

//handles reaping of the childprocesses
void sigchld_handler(int s);

//resolves the sockaddr to ease printing
void *get_in_addr(struct sockaddr *sa);

//Setups TCP socket, if hostname is NULL we want to be server
int getTCPSocket(char *hostname, char *port);

//Process child process. each child handles one browser request
void process(int sockfd, int browserfd, vector<string> keywords);




int main(int argc, char *argv[])
{
	//Proxy connection port number
	 int PORT = argv[1];

	 //Read keywords from stdin
	 vector<string> keywords;
	 string tmp;
	 while (getline(cin, tmp) && tmp != "\n")
	 {
		  keywords.push_back(tmp);
	 }

	 //uint is not standard but neccecary for UNIX system
	 unsigned int sin_size;
	 struct sigaction sa;
	 struct sockaddr_storage their_addr;
	 int sockfd = getTCPSocket(NULL, PORT);
	 if (listen(sockfd, BACKLOG) == -1)
	 {
		  cerr << "Main: listen\n";
		  return 1;
	 }

	 //Setup for child processes
	 sa.sa_handler = sigchld_handler;
	 sigemptyset(&sa.sa_mask);
	 sa.sa_flags = SA_RESTART;

	 if (sigaction(SIGCHLD, &sa, NULL) == -1)
	 {
		  cerr << "sigaction\n";
		  exit(1);
	 }
	 cout << "waiting for connection...\n";

	 //Main request - response loop
	 while (1)
	 {
	 		//Get connection from browser
		  sin_size = sizeof their_addr;
		  int browserfd = accept(sockfd, (struct sockaddr *)&their_addr, &sin_size);
		  if (browserfd == -1)
		  {
				cout << "accept" << endl;
				continue;
		  }
		  cout << "browser connected" << endl;

		  //Fork the process and handle the browser request
		  if (fork() == 0)
		  {
				process(sockfd, browserfd, keywords);
				cout << "Destroying process..." << endl;
				exit(0);
		  }
		  close(browserfd);
	 }
	 return 0;
}

bool isText(string str)
{
	//text if text/html exists and Content-Encoding doesn't
	 if (str.find("text/html") != string::npos && str.find("Content-Encoding:") == string::npos)
	 {
		  return true;
	 }
	 return false;
}

int sendall(int socket, list<string> &buffer, int flags)
{
	//sending all strings in the linked list
	 int n = 0;
	 int total = 0;
	 for (auto it = buffer.begin(); it != buffer.end(); ++it)
	 {
		  n = send(socket, const_cast<char *>((*it).c_str()), (*it).size(), flags);
		  if (n == -1)
		  {
				break;
		  }
		  total += n;
	 }
	 cout << "(sendall) Sent:" << total << " \tbyte(s)" << endl;
	 return n == -1 ? -1 : 0; // return -1 on failure, 0 on success
}

bool server_response(int socket, int browser, list<string> &res, int flags)
{
	 char buffer[1024];
	 int total_bytes = 0;
	 bool send = false;
	 bool setup = true;
	 int byte_count = 0;

	 do
	 {
		  memset(buffer, 0, sizeof buffer);
		  byte_count = recv(socket, buffer, 1024, flags);
		  if (byte_count != -1)
		  {
		  	//checks the first package to identify if plaintext
				if (!isText(string(buffer, byte_count)) && setup)
				{
					 send = true;
				}
				setup = false;

				if (send)
				{
					 list<string> l;
					 l.push_back(string(buffer, byte_count));
					 sendall(browser, l, 0);
				}
				else
				{
					 total_bytes += byte_count;
					 res.push_back(string(buffer, byte_count));
				}
		  }
	 }
	 while (byte_count > 0);

	 return send;
}

void browser_request(int socket, char *&res, int &resSize , int flags)
{
	 string allocBuffer{};
	 char buffer[1024];
	 int total_bytes = 0;

	 while (true)
	 {
		  memset(buffer, 0, sizeof buffer);
		  int byte_count = recv(socket, buffer, 1024, flags);
		  total_bytes += byte_count;
		  allocBuffer += string(buffer, byte_count);

		  //if the packages size is smaller then buffer we assume the packages have arrived
		  if (byte_count < 1024)
		  {
				res = const_cast<char *>(allocBuffer.c_str());
				resSize = total_bytes;
				break;
		  }
	 }
}

char *modify_header(char *res, int &resSize)
{
	//checking for keep-alive in the headder
	 string str(res, resSize);
	 string str2(str);
	 for_each(str2.begin(), str2.end(), [](char c)
	 {
		  return tolower(c);
	 });

	 if (str2.find("keep-alive") == string::npos)
		  return res;

	//starts the headder fixin'
	 stringstream ss(str);
	 string line;
	 string result;

	 while (getline(ss, line))
	 {
		  if (line.find("GET") != string::npos)
		  {
				int prev = line.size();
				string tmp = line;
				int p1 = 0;

				for (int i = 0 ; i < 3; i++)
				{
					 p1 = tmp.find_first_of("/", p1 + 1);
				}
				line = "GET ";
				line += tmp.substr(p1, string::npos);

				resSize -= prev;
				resSize += line.size();
		  }

		  if (line.find("Connection:") != string::npos)
		  {
				line = "Connection: close\r";
				resSize -= 5;
		  }

		  result += line + "\n";
	 }

	 cout << endl << result << endl << endl;
	 return const_cast<char *>(result.c_str());
}

bool contains_keywords(list<string> str, vector<string> keywords)
{
	//always check prev index with new to make sure keyword splited between packages are fund
	 string prev {};
	 string now {};
	 for (auto it1 = str.begin(); it1 != str.end(); ++it1)
	 {
		  now = *it1;
		  string tmp = prev + now;
		  for (auto it = keywords.begin(); it != keywords.end(); ++it)
		  {
				if (tmp.find(*it) != string::npos)
				{
					 cout << "(contains_keywords) Found: " << *it << "!" << endl;
					 return true;
				}
		  }
		  prev = now;
	 }
	 return false;
}

void sigchld_handler(int s)
{
	 while (waitpid(-1, NULL, WNOHANG) > 0);
}

void *get_in_addr(struct sockaddr *sa)
{
	 if (sa->sa_family == AF_INET)
	 {
		  return &(((struct sockaddr_in *)sa)->sin_addr);
	 }

	 return &(((struct sockaddr_in6 *)sa)->sin6_addr);
}

int getTCPSocket(char *hostname, char *port)
{
	//declare all neccecary socket data
	 int sockfd;
	 struct addrinfo hints, *servinfo, *p;
	 int rv;
	 int yes = 1;
	 char s[INET6_ADDRSTRLEN];

	 memset(&hints, 0, sizeof hints);
	 hints.ai_family = AF_UNSPEC;
	 hints.ai_socktype = SOCK_STREAM;

	 //set appropriate flag if server
	 if (hostname == NULL)
		  hints.ai_flags = AI_PASSIVE;

	 if ((rv = getaddrinfo(hostname, port, &hints, &servinfo)) != 0)
	 {
		  cerr << "(getTCPSocket) getaddrinfo" << endl;
		  return 1;
	 }

	 //We want to be client;
	 if (hostname != NULL)
	 {
		  // loop through all the results and connect to the first we can
		  for (p = servinfo; p != NULL; p = p->ai_next)
		  {

				if ((sockfd = socket(p->ai_family, p->ai_socktype, p->ai_protocol)) == -1)
				{
					 cerr << "Client socket\n";
					 continue;
				}

				if (connect(sockfd, p->ai_addr, p->ai_addrlen) == -1)
				{
					 close(sockfd);
					 cerr << "Client connect\n";
					 continue;
				}

				break;
		  }

		  if (p == NULL)
		  {
				cerr << "Client failed to connect\n";
				return -1;
		  }

		  inet_ntop(p->ai_family, get_in_addr((struct sockaddr *)p->ai_addr), s, sizeof s);
		  cout << "Client connecting to: " << s << endl;
	 }
	 //We want to be server
	 else
	 {
		  //loop and bind
		  for (p = servinfo; p != NULL; p = p->ai_next)
		  {
				if ((sockfd = socket(p->ai_family, p->ai_socktype, p->ai_protocol)) == -1)
				{
					 cerr << "Server socket \n";
					 continue;
				}

				if (setsockopt(sockfd, SOL_SOCKET, SO_REUSEADDR, &yes, sizeof(int)) == -1)
				{
					 cerr << "setsockopt\n";
					 exit(1);
				}

				if (bind(sockfd, p->ai_addr, p->ai_addrlen) == -1)
				{
					 close(sockfd);
					 cerr << "Server bind\n";
					 continue;
				}
				break;
		  }
		  if (p == NULL)
		  {
				cerr << "Server failed to bind\n";
				return 2;
		  }
	 }
	 freeaddrinfo(servinfo);
	 return sockfd;
}

void process(int sockfd, int browserfd, vector<string> keywords)
{

	 //Child process does not need sockfd
	 close(sockfd);

	 cout << "\n(process) Started processing" << endl;
	 cout << "\n(process) Reading from browser..." << endl;

	 char *bufferData;
	 int byte_count;

	 browser_request(browserfd, bufferData, byte_count, 0);
	 bufferData = modify_header(bufferData, byte_count);

	 //Extract host address
	 string hostAddr;
	 stringstream ss {bufferData};
	 while (ss >> hostAddr)
	 {
		  if (hostAddr.compare("Host:") == 0)
		  {
				ss >> hostAddr;
				break;
		  }
	 }
	 char *serverAddress = const_cast<char *>(hostAddr.c_str());

	 int serverSocket = getTCPSocket(serverAddress, (char *)("http"));
	 cout << "(process) Address: " << serverAddress << endl;

	 list<string> l;
	 l.push_back(string(bufferData, byte_count));
	 //check for keywords in browser request
	 if (contains_keywords(l, keywords))
	 {
		  cout << "(process) Redirecting Browser..." << endl;
		  char *res = (char*)("HTTP/1.1 302 Found\r\nLocation: http://www.ida.liu.se/~TDTS04/labs/2011/ass2/error1.html\r\n\r\n");
		  int msize = strlen(res);

		  send(browserfd, res, msize, 0);
	 }
	 else
	 {

		  cout << "(process) Sending to server..." << endl;
		  send(serverSocket, bufferData, byte_count, 0);

		  cout << "(process) Reading from server..." << endl;
		  list<string> allocBuffers {};
		  if (!server_response(serverSocket, browserfd, allocBuffers, 0))
		  {
				//check for keywords in server response
				if (contains_keywords(allocBuffers, keywords))
				{
					 cout << "(process) Redirecting Browser..." << endl;
					 char *res = (char*)("HTTP/1.1 302 Found\r\nLocation: http://www.ida.liu.se/~TDTS04/labs/2011/ass2/error2.html\r\n\r\n");
					 int msize = strlen(res);

					 send(browserfd, res, msize, 0);
				}
				else
				{
					 cout << "(process) Sending to browser..." << endl;
					 sendall(browserfd, allocBuffers, 0);
				}
		  }
	 }
	 cout << "(process) Closing socket..." << endl;
	 close(serverSocket);
	 close(browserfd);
}
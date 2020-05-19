package lab1;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class Proxy {
  private static ServerSocket ProxyServerSocket;
  private static List<String> UserFilter=new ArrayList<>();

  static boolean InitSocket(int port) {
    try {
      //设置端口号
      ProxyServerSocket=new ServerSocket(port);
      //设定连接最长时间
      ProxyServerSocket.setSoTimeout(1000000);
    } catch (IOException e) {
      System.out.println("初始化失败");
      return false;
    }
    return true;
    
  }

  static boolean UserFilterInit() {
    UserFilter.add("127.0.0.1");
    UserFilter.add("1.1.1.1");
    UserFilter.add("5.6.7.8");
    UserFilter.add("0.0.0.0");
    return UserFilter.size()>0;
  }
  
  
  public static void main(String[] args) {
    int ProxyPort=10240;
    System.out.println("代理服务器准备中.....");
    if (InitSocket(ProxyPort)) {
      System.out.println("开始监听端口："+ProxyPort);
    }
    else//代理服务器用于监听的套接字创建失败就退出程序。
    {
      System.exit(0);
    }
    
    //UserFilterInit();
    while (true) {
      try {
        //监听
        Socket socket=ProxyServerSocket.accept();
          
        String address=socket.getInetAddress().getHostAddress();
        for (int i=0;i<UserFilter.size();i++) {
          if (address.equals((UserFilter.get(i)))) {
            System.err.println("用户IP:"+address+"被屏蔽");
            System.exit(0);
          }
        }
        
        //创建新线程来代理客户端到服务器端的通信
        new Thread(new ProxyThread(socket)).start();
        
      } catch (IOException e) {
        System.err.println("连接超时");
      }
    
    }
  }
}

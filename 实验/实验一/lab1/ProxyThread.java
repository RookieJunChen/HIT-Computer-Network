package lab1;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class ProxyThread implements Runnable {
  // 定义http端口号，默认为80
  static int HttpPort = 80;
  static int size=100000;

  // 定义超时时间
  static int timeout = 500000;

  //客户端和代理服务器的socket
  private Socket ClientSocket = null;
  private Socket SerSocket = null;

  //socket的写入写出方法包装
  /*
  客户端上的使用
  1.getInputStream方法可以得到一个输入流，客户端的Socket对象上的getInputStream方法得到输入流其实就是从服务器端发回的数据。
  2.getOutputStream方法得到的是一个输出流，客户端的Socket对象上的getOutputStream方法得到的输出流其实就是发送给服务器端的数据。
  服务器端上的使用
  1.getInputStream方法得到的是一个输入流，服务端的Socket对象上的getInputStream方法得到的输入流其实就是从客户端发送给服务器端的数据流。
  2.getOutputStream方法得到的是一个输出流，服务端的Socket对象上的getOutputStream方法得到的输出流其实就是发送给客户端的数据。
   */
  private InputStream ClientInputStream = null;//客户端输入流
  private InputStream SeverInputStream = null;//目的服务器输入流
  private BufferedReader ClientBufferReader = null;//客户端输入字节流
  private BufferedReader SeverBufferReader = null;//目的主机输入字节流
  private OutputStream ClientOutputStream = null;//客户端输出流
  private OutputStream SeverOutputStream = null;//目的服务器输出流
  private PrintWriter ClientPrintWriter = null;//客户端输出字节流
  private PrintWriter SeverPrintWriter = null;//目的主机输出字节流

  // 利用键值对进行缓存
  // 对象被缓存的具体时间
  static Map<String, String> timecache = new HashMap<>();

  // 对象被缓存的具体数据
  static Map<String, List<Byte>> bytescache = new HashMap<>();

  //用于网站过滤的列表
  static List<String> WebsiteFilter=new ArrayList<>();
  
  //用于钓鱼
  static Map<String,String> fishguide=new HashMap<>();
  
  
  public ProxyThread(Socket clientsocket) throws IOException {
    super();
    this.ClientSocket = clientsocket;
    this.ClientInputStream = clientsocket.getInputStream();
    this.ClientBufferReader = new BufferedReader(new InputStreamReader(ClientInputStream));
    this.ClientOutputStream = clientsocket.getOutputStream();
    this.ClientPrintWriter = new PrintWriter(ClientOutputStream);
    
    WebsiteFilter.add("jwts.hit.edu.cn");
    fishguide.put("jwes.hit.edu.cn", "http://www.hit.edu.cn/");
    
  }

  /**
   * 解析http头的信息，获取method，url，host，cookie
   */
  public HttpHeader ParseHeader(List<String> header) {
    String firstLine = header.get(0);
    String method = null;
    String url = null;
    String host = null;
    String cookie = null;
    if (firstLine.charAt(0) == 'G') {
      method = "GET";
      url = firstLine.substring(4, firstLine.length() - 9);
    } else if (firstLine.charAt(0) == 'P') {
      method = "POST";
      url = firstLine.substring(5, firstLine.length() - 9);
    } else {
      method = "CONNECT";//https里的特殊情况
    }
    //切出Host和Cookie的信息
    for (int i = 0; i < header.size(); i++) {
      if (header.get(i).startsWith("Host")) {
        host = header.get(i).substring(6, header.get(i).length());
      } else if (header.get(i).startsWith("Cookie")) {
        cookie = header.get(i).substring(8, header.get(i).length());
      }
    }
    HttpHeader httpHeader = new HttpHeader(method, url, host, cookie);
    return httpHeader;
  }


  /**
   * 获取代理服务器与服务器沟通的套接字
   * @param host 主机名
   * @param port 端口
   * @param times 连接次数
   * @return 代理服务器与服务器的沟通的套接字
   * @throws UnknownHostException
   * @throws IOException
   */
  public Socket ConnectToServer(String host, int port, int times) throws UnknownHostException, IOException {
    for (int i = 0; i < times; i++) {
      SerSocket = new Socket(host, port);
      //设置端口连接时间
      SerSocket.setSoTimeout(timeout);
      SeverInputStream = SerSocket.getInputStream();
      SeverBufferReader = new BufferedReader(new InputStreamReader(SeverInputStream));
      SeverOutputStream = SerSocket.getOutputStream();
      SeverPrintWriter = new PrintWriter(SeverOutputStream);

      if (SerSocket != null) {
        return SerSocket;
      }
    }
    return null;
  }

  /**
   * 代理服务器向服务器发送请求信息
   * @param lst 请求信息
   */
  public void SendToServer(List<String> lst) {
    System.out.println("\n=========代理服务器向服务器发送请求信息=========");
    for (int i = 0; i < lst.size(); i++) {
      String line = lst.get(i);
      SeverPrintWriter.write(line + "\r\n");
      System.out.println(line);
    }
    SeverPrintWriter.write("\r\n");
    SeverPrintWriter.flush();
  }
  
  /**
   * 没有缓存的情况下，代理服务器从服务器转发响应信息到客户端
   * @param url
   * @return
   */
  public boolean SendBackToClient(String url) {
    /*
     * 必须采用bytes数组，否则由于ASCII码与unicode编码的差异，无法识别
     */
    
    System.out.println("\n=========转发响应信息到客户端=========\n");
    
    List<Byte> lst=new ArrayList<>();
    
    try {
      // String time = null;
      byte bytes[] = new byte[size];
      int len;
      while (true) {
        if ((len = SeverInputStream.read(bytes)) >= 0) {
          ClientOutputStream.write(bytes, 0, len);
          for (int i=0;i<len;i++) {
            lst.add(bytes[i]);
          }
        } else if (len < 0) {
          break;
        }
      }
      
      byte b[]=new byte[lst.size()];
      for (int i=0;i<lst.size();i++) {
        b[i]=lst.get(i);
      }
      //将二进制数组转为字符串
      String s=new String(b);
      String time=findTime(s);
      timecache.put(url, time);
      bytescache.put(url, lst);
      
      ClientPrintWriter.write("\r\n");
      ClientPrintWriter.flush();
      ClientOutputStream.close();
    } catch (IOException e) {
    } catch (Exception e) {
    }
    return true;
  }
  
  /**
   * 根据字符串获取其中的Date时间
   * @param s
   * @return
   */
  public String findTime(String s) {
    int begin=s.indexOf("Date");
    int end=s.indexOf("GMT");
    //System.out.println(s.substring(begin+6, end+3));
    return s.substring(begin+6, end+3);
  }

  /**
   * 有缓存的情况下，给客户端需要的信息
   * 1.在缓存时间后服务器没有修改对象，则将缓存直接发送给客户端
   * 2.在缓存时间后服务器修改对象了，则将Sever的新对象发送给客户端
   * @param header
   * @param host
   * @param url
   * @return
   */
  public boolean SendBackToClientWithCache(List<String> header,String host, String url) {
    String modifiTime=timecache.get(url);
    // 发送条件性GET方法到服务器
    SeverPrintWriter.write(header.get(0) + "\r\n");
    SeverPrintWriter.write("Host: "+host + "\r\n");
    
    System.out.println("Modified Time:"+modifiTime);
    String str = "If-modified-since: " + modifiTime + "\r\n";
    SeverPrintWriter.write(str);
    SeverPrintWriter.write("\r\n");
    SeverPrintWriter.flush();

    try {
      String ServerMessage = SeverBufferReader.readLine();
      //System.out.println(ServerMessage);
      if (ServerMessage == null) {
        return false;
      }
      System.err.println("\n服务器响应信息首部："+ServerMessage);
      // 如果服务器在缓存时间后未修改对象，直接转发给客户端缓存
      if (ServerMessage.contains("Not Modified")) 
      {
        System.err.println("\n=======缓存未更新======");
        List<Byte> lst=bytescache.get(url);
        byte bytes[]=new byte[lst.size()];
        for (int i=0;i<lst.size();i++) {
          bytes[i]=lst.get(i);
        }
        ClientOutputStream.write(bytes);
        ClientPrintWriter.write("\r\n");
        ClientPrintWriter.flush();
        ClientPrintWriter.close();
      }
      //如果修改过对象，则将新的对象按字节发给客户端
      else if (ServerMessage.contains("OK")) 
      {
        System.err.println("\n=======缓存已更新======");
        DataOutputStream d=new DataOutputStream(ClientOutputStream);
        byte[] b=(ServerMessage+"\r\n").getBytes();
        d.write(b);
        //ClientPrintWriter.write("\r\n");
        byte bytes[] = new byte[size];
        int len;
        while (true) {
          if ((len = SeverInputStream.read(bytes)) > 0) {
            ClientOutputStream.write(bytes, 0, len);
          } else if (len < 0) {
            break;
          }
        }
        //缓存无效，去除.
        timecache.remove(url);
        bytescache.remove(url);

        
        ClientPrintWriter.write("\r\n");
        ClientPrintWriter.flush();
        ClientOutputStream.close();
      }
      else {  //http消息无效的情况
        bytescache.remove(url);
        timecache.remove(url);
        while (!SeverBufferReader.readLine().equals("")) 
        {
          //刷完无效信息
        }
        byte bytes[] = new byte[size];
        int len;
        while (true) {
          if ((len = SeverInputStream.read(bytes)) >= 0) {
            //ClientOutputStream.write(bytes, 0, len);
          } else if (len < 0) {
            break;
          }
        }
        run();//重新来一遍
      }
    } catch (IOException e1) {
    }

    return true;
  }
  
  public void Filter() {
    for (int i=1;i<419;i++) {
      ClientPrintWriter.write("非法网站！FBI WARNING!\t\t");
      if (i%11==0) {
        ClientPrintWriter.write("\r\n");
      }
    }
    ClientPrintWriter.write("\r\n");
    ClientPrintWriter.flush();
    ClientPrintWriter.close();
  }
  
  public void fishing(List<String> header){

    header.clear();
    header.add("GET http://www.hit.edu.cn/ HTTP/1.1");
    header.add("Host: www.hit.edu.cn");
    header.add("User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:70.0) Gecko/20100101 Firefox/70.0");
    header.add("Accept: text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
    header.add("Accept-Language: zh-CN,zh;q=0.8,zh-TW;q=0.7,zh-HK;q=0.5,en-US;q=0.3,en;q=0.2");
    header.add("Accept-Encoding: gzip, deflate");
    header.add("Connection: keep-alive");
    header.add("Upgrade-Insecure-Requests: 1");
    HttpHeader httpHeader=ParseHeader(header);
    
    String host=httpHeader.host;
    String url=httpHeader.url;
    /*
     * 获取代理服务器与服务器的Socket
     */
    try {
      if (ConnectToServer(host, HttpPort, 5) == null) {
        return;
      }
    } catch (UnknownHostException e) {
    } catch (IOException e) {
    }
    System.out.println("url="+url);
    System.out.println("host="+host);
    
    boolean flag=timecache.containsKey(url)&&bytescache.containsKey(url);
    if (!flag) {
      /*
       * 没有Cache的情况
       */
      System.err.println("\n=========无缓存=========");
      SendToServer(header);
      SendBackToClient(url);
    } else {
      /*
       * 有Cache的情况
       */
      System.err.println("\n=========有缓存=========");
      SendBackToClientWithCache(header, host,url);
    }


    
}   


  @Override
  public void run() {
    try {
      ClientSocket.setSoTimeout(timeout);
      String line = null;
      List<String> header = new ArrayList<>();
      // 获取从客户端发送的请求信息
      line = ClientBufferReader.readLine();
      if (line == null) {
        return;
      }
      header.add(line);
      System.out.println("\n=========客户端请求=========");
      System.out.println(line);
      while (!(line = ClientBufferReader.readLine()).equals("")) {
        header.add(line);
        System.out.println(line);
      }
      
      //解析报文信息获取http信息
      HttpHeader httpHeader = ParseHeader(header);
      String url = httpHeader.url;
      String host = httpHeader.host;

     //网站引导（钓鱼） 
      for (String h:fishguide.keySet()) {
        if (host.contains(h)) {
          fishing(header);
          return;
        }
      }
      //不处理https里的CONNECT方法
      if (httpHeader.method.equals("CONNECT")) {
        return;
      }

     //网站过滤
      for (int i=0;i<WebsiteFilter.size();i++) {
        if (host.contains(WebsiteFilter.get(i))) {
          Filter();
          System.err.println("禁止访问："+url);
          return;
        }
      }
      
            
      //获取代理服务器与服务器的Socket

      if (ConnectToServer(host, HttpPort, 5) == null) {
        return;
      }
      System.out.println("url="+url);
      System.out.println("host="+host);
      
      boolean flag= timecache.containsKey(url)&&bytescache.containsKey(url);
      if (!flag) {
       // 没有Cache的情况
        System.err.println("\n=========无缓存=========");
        SendToServer(header);
        SendBackToClient(url);
      } else {
       // 有cache的情况
        System.err.println("\n=========有缓存=========");
        SendBackToClientWithCache(header, host,url);
      }

    } catch (SocketException e) {
      //e.printStackTrace();
    } catch (IOException e) {
      //e.printStackTrace();
    }catch (Exception e){
      //e.printStackTrace();
    }

  }
}
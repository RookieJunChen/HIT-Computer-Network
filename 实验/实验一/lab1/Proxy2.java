package lab1;

import java.io.*;
import java.net.*;
import java.util.*;
 
//无法完成https协议
public class Proxy2 extends Thread {
  public static int CONNECT_RETRIES = 5; // 尝试与目标主机连接次数
  public static int CONNECT_PAUSE = 5; // 每次建立连接的间隔时间
  public static int TIMEOUT = 8000; // 每次尝试连接的最大时间
  public static int BUFSIZ = 1024; // 缓冲区最大字节数
  public static boolean logging = false; // 是否记录日志
  public static OutputStream log_S = null; // 日志输出流
  public static OutputStream log_C = null; // 日志输出流
  public static OutputStream log_D = null; // 响应报文日志
  public static int count = -1;
  public static List<String> requestInfo = new ArrayList<String>();
  public static List<String> cacheInfo;
  public static Map<String, String> cache = new HashMap<String, String>();
  Socket ssocket = null;
  // cis为客户端输入流，sis为目标主机（服务器）输入流
  InputStream cis = null, sis = null;
  BufferedReader cbr = null, sbr = null; // 转化为字符流读取便于比较
  // cos为客户端输出流，sos为目标主机输出流
  OutputStream cos = null, sos = null;
  PrintWriter cpw = null, spw = null;// 转化为字符流
  String buffer = ""; // 读取请求头
  String URL = ""; // 读取请求URL
  String host = ""; // 读取目标主机host
  int port = 80; // 端口号为80
  // 与客户端相连的Socket
  protected Socket csocket;
 
  //类的初始化
  public Proxy2(Socket cs) {
    try {
      csocket = cs;
      cis = csocket.getInputStream(); // 代理服务器作为服务器接受客户端的请求
      cbr = new BufferedReader(new InputStreamReader(cis));
      cos = csocket.getOutputStream(); // 代理服务器作为服务器向客户端发出响应
      cpw = new PrintWriter(cos);
      start();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
 
  //用于写日志的函数
  public void writeLog(int c, int browser) throws IOException {
    if (browser == 1)
      log_C.write((char) c);
    else if (browser == 2)
      log_S.write((char) c);
    else
      log_D.write((char) c);
  }
 
  //用于写日志的函数
  public void writeLog(byte[] bytes, int offset, int len, int browser)
      throws IOException {
    for (int i = 0; i < len; i++)
      writeLog((int) bytes[offset + i], browser);
  }
 
  public void run() {  
      try { 
        csocket.setSoTimeout(TIMEOUT);
        buffer = cbr.readLine(); // 获取首部行
        System.out.println("首部行：" + buffer);
        if(buffer.contains("CONNECT"))
        {
          return;
        }
        URL = getRequestURL(buffer);
        System.out.println(URL);
        //网站引导（钓鱼）
        if(URL.equals("http://www.sogou.com/")){
          URL = "http://www.taobao.com/";
          buffer = "GET "+URL+" HTTP/1.1"; 
          requestInfo.add("Accept: text/html, application/xhtml+xml, */*"); 
          requestInfo.add("Accept-Language: zh-Hans-CN,zh-Hans;q=0.8,en-US;q=0.5,en;q=0.3"); 
          requestInfo.add("User-Agent: Mozilla/5.0 (compatible; MSIE 9.0; Windows NT 6.2; WOW64; Trident/6.0)");
          requestInfo.add("Accept-Encoding: gzip, deflate");
          requestInfo.add("Proxy-Connection: Keep-Alive");
          requestInfo.add("DNT: 1");
          requestInfo.add("Host: www.taobao.com");
          requestInfo.add("Cookie: thw=cn; isg=0BC4B5EFD7C7FCFEB73317770EA7F3F5; l=AeVoHE44ZTsle7DjpW8fBSV7pbSl-2U7; cna=GCHeDZQAVwkCAdvZ9Apwg8rH; t=1a1386bec550ab78d1aaf5ad5b90e044; mt=ci%3D-1_0; _med=dw:1366&dh:768&pw:1366&ph:768&ist:0");
        }
        else if(URL.equals("http://www.qq.com/")) {
          URL = "";//为了后面屏蔽的操作
        }
        int n;
        // 抽取host
        n = URL.indexOf("//");
        if (n != -1)
          host = URL.substring(n + 2); // 切出www.xxxxx.com/形式
        n = host.indexOf('/');
        if (n != -1)
          host = host.substring(0, n);//切出www.xxxxx.com形式

 
        // 分析可能存在的端口号
        n = host.indexOf(':');
        if (n != -1) {
          port = Integer.parseInt(host.substring(n + 1));//切出端口号来
          host = host.substring(0, n);//切掉端口号的部分
        }
        //按限定的连接尝试次数进行连接
        int retry = CONNECT_RETRIES;
        while (retry-- != 0 && !host.equals("")) {
          try {
            System.out.println("端口号：" + port + "主机：" + host);
            System.out.println("剩余连接次数： " + retry);
            ssocket = new Socket(host, port); // 尝试建立与目标主机的连接
            break;
          } catch (Exception e) {
            //e.printStackTrace();
          }
          // 未成功建立连接则等待
          Thread.sleep(CONNECT_PAUSE);
        }
        if (ssocket != null) 
        {
          //设定连接最长时间
          ssocket.setSoTimeout(TIMEOUT);
          sis = ssocket.getInputStream(); // 代理服务器作为客户端接受响应
          sbr = new BufferedReader(new InputStreamReader(sis));
          sos = ssocket.getOutputStream(); // 代理服务器作为客户端发出请求
          spw = new PrintWriter(sos);
          
          String modifTime = findCache(URL);// 在缓存中寻找是否之前已经缓存过这个url的信息
          System.out.println("上一次修改的时间为：" + modifTime);
          writeLog(buffer.getBytes(), 0, buffer.length(), 1);
          writeLog(buffer.getBytes(), 0, buffer.length(), 3);
          writeLog("\r\n".getBytes(), 0, 2, 3);
          int i = 0;
          // 之前没有缓存
          if (modifTime == null) 
          {
            while (!buffer.equals("")) //屏蔽设定的网站，未屏蔽的网站通过其循环读
            {
              buffer += "\r\n";
              if(i == 0)
                System.out.println("替客户端向服务器发送请求：");
              if(buffer.contains("www.taobao.com")) //如果是钓鱼网站就发送钓鱼网站的报文
              { 
                int k = 0;
                while(requestInfo.size() > k)
                {
                  spw.write(buffer);//发送相应的报文
                  buffer = requestInfo.get(k++);
                  buffer += "\r\n";
                  cache.replace(URL, cache.get(URL) + buffer);
                }
                break;
              }
              else
              { 
                //正常发送
                spw.write(buffer); 
                writeLog(buffer.getBytes(), 0, buffer.length(), 1);
                System.out.print(buffer);
                buffer = cbr.readLine();
                
                
              }
              i++;
            }
            spw.write("\r\n");
            writeLog("\r\n".getBytes(), 0, 2, 1);
            spw.flush();
            
            
            // 读取服务器的响应信息
//            int length;
//            byte bytes[] = new byte[BUFSIZ];
            int k = 0;
            String info = sbr.readLine();
            System.out.println("服务器响应：");
            while (!info.equals("")) 
            {
              try 
              {
                info += "\r\n";
                if(k == 0)
                {
                  cache.put(URL,info);
                }
                else
                  cache.replace(URL, cache.get(URL) + info);
                System.out.print(info);
                cpw.write(info);
                info = sbr.readLine();
                k++;
                System.out.println(k);
                
//                if ((length = sis.read(bytes)) > 0) 
//                { // 读取客户端的请求转给服务器，直接用流来转，更高效
//                  cos.write(bytes, 0, length);
//                  if (logging) 
//                  {
//                    writeLog(bytes, 0, length, 1);
//                    writeLog(bytes,0,length,3);
//                  }
//                } else if (length < 0)
//                {
//                    break;//读完后跳出循环
//                }
              } catch (Exception e) 
              {
                break;
              } 
            } 
            
//            if(count == 0) 
//            {
//              System.out.println(cbr.readLine());
//            }
            cpw.write("\r\n");
            
            if(logging)
            {
              writeLog("\r\n".getBytes(), 0, 2, 3);
              writeLog("\r\n".getBytes(), 0, 2, 2);
            }
            
            cpw.flush();
            
            
            
            try 
            {
              File file = new File(URL.replace(":", "").replace("/", "")+ ".txt");
//              if(!file.exists())
//              {
//                file.createNewFile();
//              }
              System.out.println(1);
              FileWriter fw = new FileWriter(file,false);
              System.out.println("成功创建缓存："+URL + ".txt" );
              System.out.println(cache.get(URL));
              fw.write(cache.get(URL));
              
            } catch (Exception e) {
              e.printStackTrace();
            }
          } 
          
          
          else //有缓存的情况
          {
            buffer += "\r\n";
            //构造并向服务器端发送条件性get方法
            spw.write(buffer);
            System.out.print("向服务器发送确认修改时间请求:");
            
            String str1 = "Host: " + host + "\r\n";
            spw.write(str1);
            System.out.println(str1);
            String str = "If-modified-since: " + modifTime + "\r\n";
            spw.write("\r\n");
            spw.flush();
            System.out.println(buffer);
            System.out.print(str1);
            System.out.print(str);
 
            String info = sbr.readLine();
            System.out.println("服务器发回的信息是："+info);//主要就看第一行信息
            if (info.contains("304")) //缓存为最新
            {
              int j = 0;
              System.out.println("缓存为最新，使用缓存中的数据。");
              while (j < cacheInfo.size())
              {
                info = cacheInfo.get(j++);
                info += "\r\n";
                System.out.print(info);
                cpw.write(info);
              }
              cpw.write("\r\n");
              cpw.flush();
            } 
            else 
            {
              System.out.println("有更新，使用新的数据");
              int j = 0;
              while (!info.equals("")) 
              {
                info += "\r\n";
                if(j == 0)
                {
                  System.out.println("新的数据是：" );
                  cache.replace(URL, info);
                }
                else
                  cache.replace(URL, cache.get(URL) + info);
                j++;
                
                System.out.print(info);
                cpw.write(info);
                info = sbr.readLine();
              }
              cpw.write("\r\n");
              cpw.flush();
              File file = new File(URL + ".txt");
              try {
                if(!file.exists())
                {
                  file.createNewFile();
                }
                FileWriter fw = new FileWriter(file,false);
                System.out.println("成功更新缓存："+URL + ".txt" );
                System.out.println(cache.get(URL));
                fw.write(cache.get(URL));
                
              } catch (Exception e) {
                // TODO: handle exception
              }
            }
          }
        }
      } catch (Exception e) {
        e.printStackTrace();
      } 
  }
 
  //从缓冲区中提取出URL字符串的方法
  public String getRequestURL(String buffer) 
  {
    String[] tokens = buffer.split(" ");
    String URL = "";
    if (tokens[0].equals("GET"))
      for (int index = 0; index < tokens.length; index++) 
      {
        if (tokens[index].startsWith("http://")) 
        {
          URL = tokens[index];
          break;
        }
      }
    return URL;
  }
 

 
  public static void startProxy(int port, Class clobj) {
    try {
      ServerSocket ssock = new ServerSocket(port);
      while (true) 
      {
        Class[] sarg = new Class[1];
        Object[] arg = new Object[1];
        sarg[0] = Socket.class;
        try 
        {
          java.lang.reflect.Constructor cons = clobj.getDeclaredConstructor(sarg);
          //接收队列里的第一个
          arg[0] = ssock.accept();
          System.out.println("启动线程："+count++);
          cons.newInstance(arg); // 创建HttpProxy或其派生类的实例
        } catch (Exception e) 
        {
          Socket esock = (Socket) arg[0];
          try 
          {
            esock.close();
          } catch (Exception ec) 
          {
          }
        }
      }
    } catch (IOException e) {
      System.out.println("\nStartProxy Exception:");
      e.printStackTrace();
    }
  }
 
  // 测试用的简单main方法
  static public void main(String args[]) throws FileNotFoundException {
    System.out.println("在端口10240启动代理服务器\n");
    OutputStream file_S = new FileOutputStream(new File("log_s.txt"));
    OutputStream file_C = new FileOutputStream(new File("log_c.txt"));
    OutputStream file_D = new FileOutputStream(new File("log_d.txt"));
    Proxy2.log_S = file_S;
    Proxy2.log_C = file_C;
    Proxy2.log_D = file_D; // 直接存储相关URl对应的响应报文
    Proxy2.logging = true;
    Proxy2.startProxy(10240, Proxy2.class);
  }
 
  public String findCache(String head) {
    cacheInfo = new ArrayList<String>();
//    String resul = null;
//    try {
//      // 直接在存有url和相应信息的文件中查找
//      InputStream file_D = new FileInputStream("log_d.txt");
//      String info = "";
//      while (true) 
//      {
//        int c = file_D.read();
//        if (c == -1)
//          break; // -1为结尾标志
//        if (c == '\r') 
//        {
//          file_D.read();
//          break;// 读入每一行数据
//        }
//        if (c == '\n')
//          break;
//        info = info + (char) c;
//      }
//      System.out.println("第一次得到：" + info);
//      System.out.println("要找的是：" + head);
//      int m = 0;
//      while ((m = file_D.read()) != -1 && info!=null) {
//        //System.out.println("在寻找："+info);
//        // 找到相同的，那么它下面的就是响应信息，找上次修改的时间
//        if (info.contains(head)) {
//          String info1;
//          do {
//            System.out.println("找到相同的了：" + info);
//            info1 = "";
//            if(m!='\r' && m != '\n')
//              info1 += (char) m;
//            while (true) { 
//              m = file_D.read();
//              if (m == -1)
//                break;
//              if (m == '\r') {
//                file_D.read(); 
//                break;
//              }
//              if (m == '\n') { 
//                break; 
//              }
//              info1 += (char) m;
//            }
//            System.out.println("info1是："+info1);
//            if (info1.contains("Last-Modified:")) {
//              resul = info1.substring(16); 
//            } 
//            cacheInfo.add(info1);
//            if(info1.equals("")){ 
//              System.out.print("我是空");
//              return resul;
//            } 
//          } while (!info1.equals("") && info1 != null && m != -1);
//        }
//        info = "";
//        while (true) { 
//          if (m == -1)
//            break;
//          if (m == '\r') {
//            file_D.read();
//            break;
//          }
//          if (m == '\n')
//            break;
//          info += (char) m;
//          m = file_D.read();
//        }
//      }
//    } catch (FileNotFoundException e) { 
//      e.printStackTrace();
//    } catch (IOException e) {
//      e.printStackTrace();
//    }
// 
//    return resul;
    return null;
  }
 
}

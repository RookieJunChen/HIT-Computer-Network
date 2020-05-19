package lab2;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.TimerTask;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class StopWaitD1 implements Runnable{

	private static int seqnum=16;
	
	private static int N = 1;
	private int choice;
	
	public StopWaitD1(int choice) {
		super();
		this.choice = choice;
	}

	@Override
  public void run() {

	  if (choice==0) {
      Send();
    }
    else if (choice==1){
      Receive();
    }

  }

  public static void main(String[] args) {
  	new Thread(new StopWaitD1(0)).start();
  	new Thread(new StopWaitD1(1)).start();
  }

  // 发送数据部分
	private static int SendDataPort = 10241;//接收方端口号
	private static int ReceiveAckPort = 10240;//发送方端口号
	private static DatagramSocket SenderSocket;
	private static DatagramPacket SendDataPacket;
	private static DatagramSocket ReceiveAckSocket;
	private static DatagramPacket ReceiverAckPacket;
	private static int send_base = 0;
	private static int nextseqnum = 0;
	private static boolean flag = false;//是否开始计时的标志
	private static int timeout = 2;//超时时间定为5s
	private static String filestr = new String();
	private static byte[] B;
	private static int team;
	private static int times = 1;
	private ScheduledExecutorService executor;
	
	
	/**
	 * 开始计时或者重新计时，超时时间为5s
	 */
	public void TimeBegin() 
	{
		TimerTask task=new TimerTask() 
		{
			@Override
			public void run() 
			{
			  //UDP数据包每次能够传输的最大长度 = MTU(1500B) - IP头(20B) -UDP头（8B）= 1472Bytes
			  //没有信息发送时就返回。
				if (send_base>= Math.ceil(B.length / 1469)-1) //取大于等于的最近整数
				{
				  executor.shutdown();
				  return;
				}
				try 
				{
				  //重新开始计时并发送
						byte[] tempb = GetPart(send_base);
						String temp = new String(tempb);
//						String s = new String(send_base%seqnum + ":" + temp);
						String s;
		        if(send_base%seqnum < 10)
		        {
		          s = new String("0" + send_base%seqnum + ":" + temp);
		        }
		        else
		        {
		          s = new String(send_base%seqnum + ":" + temp);
		        }
						byte[] data = s.getBytes();
						DatagramPacket SenderPacket = new DatagramPacket(data, data.length, InetAddress.getLocalHost(),SendDataPort);
						//接收端地址也是本地
						SenderSocket.send(SenderPacket);
						System.out.println("重发分组:" + send_base%seqnum+" 重发的是第"+send_base+"个包");
						TimeBegin();
				} catch (Exception e) {
				}
			}
		};
		
		if (!flag) {
			flag = true;
		} else {
		  executor.shutdown();
		}
		executor=Executors.newSingleThreadScheduledExecutor();
		executor.scheduleWithFixedDelay(task, timeout, timeout, TimeUnit.SECONDS);
		
	}

	/**
	 * 结束计时
	 */
	public void TimeEnd() 
	{
		if (flag) 
		{
			executor.shutdown();
			flag = false;
		}
	}

	/**
   * 重置计时器
   */
  public void TimeReset() 
  {
  	if (send_base == nextseqnum)
  	{
  		TimeEnd();
  	} 
  	else 
  	{
  		TimeBegin();
  	}
  }

  /**
	 * 读取文件，存入filestr和字节数组B中
	 * 
	 * @param filename
	 */
	public static void ReadFilebyLines(String filename) 
	{
	  filename = "src\\lab2\\"+filename;
	  File file = new File(filename);
		BufferedReader reader = null;
		try 
		{
			reader = new BufferedReader(new FileReader(file));
			String tempstr = null;
			while ((tempstr = reader.readLine()) != null) 
			{
				filestr += tempstr + "\r\n";
			}
			reader.close();
			B = filestr.getBytes();

		} catch (IOException e) 
		{
			e.printStackTrace();
		} finally 
		{
			if (reader != null) 
			{
				try 
				{
					reader.close();
				} catch (IOException e1) 
				{
				}
			}
		}
	}

	/**
	 * 根据nextseqnum获得要传输的字节切片(分块传输）
	 * 
	 * @param nextseqnum
	 *            要传输的分组序号
	 * @return 字节数组，要传输的字节
	 */
	public byte[] GetPart(int nextseqnum) 
	{
		byte[] temp = new byte[1469];
		for (int i = 0; i < 1469; i++) 
		{
			if (nextseqnum * 1469 + i >= B.length) 
			{
				break;
			}
			temp[i] = B[nextseqnum * 1469 + i];
		}
		return temp;
	}

	/**
	 * 发送数据
	 */
	public void Send() 
	{
		ReadFilebyLines("data.txt");
		team=(int) Math.ceil(B.length/1469);
		try 
		{
			SenderSocket = new DatagramSocket();
			ReceiveAckSocket = new DatagramSocket(ReceiveAckPort);
			while (true) 
			{
				SendtoReciver();
				ReceiveACK();
				if (send_base >= team) 
				{
				  break;
				}
			}
			executor.shutdown();
			System.out.println("发送结束.");
		} catch (Exception e) {
		}
	}

	/**
	 * 发送数据给接收方
	 */
	public void SendtoReciver() 
	{
		try 
		{
			//以窗口长度为判定界限.
		  while (nextseqnum < send_base + N) 
			{
				if (send_base>= team||nextseqnum>=team) 
				{
					break;
				}
				byte[] tempb = GetPart(nextseqnum);
				String temp = new String(tempb);
				//给数据段添加上标记序列号的首部
//				String s = new String(nextseqnum%seqnum + ":" + temp);
				String s;
        if(nextseqnum%seqnum < 10)
        {
          s = new String("0" + nextseqnum%seqnum + ":" + temp);
        }
        else
        {
          s = new String(nextseqnum%seqnum + ":" + temp);
        }
				byte[] data = s.getBytes();
				SendDataPacket = new DatagramPacket(data, data.length, InetAddress.getLocalHost(), SendDataPort);
				// 模拟数据包丢失
				if (nextseqnum % 5 != 0) 
				{
					SenderSocket.send(SendDataPacket);
					System.out.println("发送分组:" + nextseqnum%seqnum+" 发送的是第"+nextseqnum+"个包");
				} 
				else 
				{
					System.out.println("模拟分组" + nextseqnum%seqnum + "丢失"+" 丢失的是第"+nextseqnum+"个包");
				}
				if (send_base == nextseqnum) 
				{
					TimeBegin();
				}
				nextseqnum++;

			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * 接收ACK
	 * @throws InterruptedException 
	 */
	public void ReceiveACK() throws InterruptedException 
	{
		try {
		  //发送完数据的情况
			if (send_base>=team) 
			{
				return;
			}
			//ACK消息的大小为10bytes.
			byte[] bytes = new byte[10];
			ReceiverAckPacket = new DatagramPacket(bytes, bytes.length);
			ReceiveAckSocket.receive(ReceiverAckPacket);
			String ackString = new String(bytes, 0, bytes.length);
			String acknum = new String();
			for (int i = 0; i < ackString.length(); i++) 
			{
				//取出ACK信息中的完成ACK序列号
			  if (ackString.charAt(i) >= '0' && ackString.charAt(i) <= '9') 
				{
					acknum += ackString.charAt(i);
				} 
				else 
				{
					break;
				}
			}
			int ack = Integer.parseInt(acknum);
			if (ack % 6 == 0 && times == 1) 
      {
        System.out.println("模拟ACK" + ack + "丢失");
        times++;
      } 
      else 
      {
        System.out.println("接收到"+ "ACK" + ack);
        int m;
        //越序号列的情况.
        if (((send_base%seqnum)>ack)&&((nextseqnum/seqnum)>(send_base/seqnum))&&(ack<=((send_base+N)%N))) 
        {
          m=send_base/seqnum*seqnum+ack+seqnum+1;
        }
        else 
        {
          m=send_base/seqnum*seqnum+ack+1;
        }
        send_base = Math.max(send_base, m);
      }
			TimeReset();
		} catch (IOException e) {
		}
	}

//接收数据部分
 private static int SendAckPort = 10243;
 private static int ReceiveDataPort = 10242;
 private static DatagramSocket ReciverSocket;
 private static DatagramPacket SendAckPacket;
 private static int expectedSeqNum = 0;

 private static int last=-1;
 


 /**
  * 接收数据并发回ACK
  */
 public void Receive() {
   try {
     ReciverSocket = new DatagramSocket(ReceiveDataPort);
     while (true) 
     {
       byte[] data= new byte[1472];
//       if(expectedSeqNum <= 9)
//         data = new byte[1471];
//       else
//         data = new byte[1472];
       DatagramPacket packet = new DatagramPacket(data, data.length);
       ReciverSocket.receive(packet);
       byte[] d = packet.getData();
       String message = new String(d);
       String num = new String();
       for (int i = 0; i < message.length(); i++) 
       {
         if (message.charAt(i) <= '9' && message.charAt(i) >= '0') 
         {
           num = num + message.charAt(i);
         } else {
           break;
         }
       }
       // 进行累积确认，不是想要的序号段的话直接丢弃
       if (expectedSeqNum == Integer.valueOf(num)) 
       {
         int ack = expectedSeqNum;
         SendACK(ack);
         expectedSeqNum = (expectedSeqNum + 1)%seqnum;
         last=ack;
       }
       else 
       {
         if (last>=0) 
         {
           SendACK(last);
         }
       }
     }
   } catch (Exception e) {
   }
 }

 /**
  * 发回ACK
  * 
  * @param ack
  *            发回的ACK序号，为0到N-1
  */
 public void SendACK(int ack) {
   try {
     ACK ACK = new ACK(ack);
     SendAckPacket = new DatagramPacket(ACK.ackByte, ACK.ackByte.length, InetAddress.getLocalHost(),SendAckPort);
     ReciverSocket.send(SendAckPacket);
     System.out.println("发回ACK" + ack);
   } catch (Exception e) {
   }
 }
	
}


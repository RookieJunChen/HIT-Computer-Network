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

public class SRS implements Runnable{

private static int seqnum = 16;


	public SRS() {
		super();

	}

	@Override
  public void run()
  {
  		Send();
  }

  public static void main(String[] args) {
  	new Thread(new SRS()).start();
//  	new Thread(new SRS(1)).start();
  }

  // 发送数据部分
	private static int N = 8;
	private static int SendDataPort = 10241;
	private static int ReceiveAckPort = 10240;
	private static DatagramSocket SenderSocket;
	private static DatagramPacket SendDataPacket;
	private static DatagramSocket ReceiveAckSocket;
	private static DatagramPacket ReceiverAckPacket;
	private static int send_base = 0;
	private static int nextseqnum = 0;
	private static int timeout = 4;
	 private static int times = 1;
	private static String filestr = new String();
	private static byte[] B;
	private static int team;
	private static boolean[] ackarray = new boolean[N];//判断每个序列号是否被接收方接收到了的标志数组
	private static boolean[] flags = new boolean[N];//判断每个序列号的计时器是否开启的标志数组
	private static ScheduledExecutorService[] executors = new ScheduledExecutorService[N];

	/**
	 * 开始计时或者重新计时，超时时间为2s
	 */
	public void TimeBegin(int q, int x) {
		TimerTask task = new TimerTask() {
			@Override
			public void run() {
				if (send_base >= Math.ceil(B.length / 1469)-1) {
					return;
				}
				try {
					byte[] tempb = GetPart(x);
					String temp = new String(tempb);
					String s;
					if(nextseqnum%seqnum < 10)
	        {
	          s = new String("0" + x%seqnum + ":" + temp);
	        }
	        else
	        {
	          s = new String(x%seqnum + ":" + temp);
	        }
					byte[] data = s.getBytes();
					DatagramPacket SenderPacket = new DatagramPacket(data, data.length, InetAddress.getLocalHost(),SendDataPort);
					SenderSocket.send(SenderPacket);
					System.out.println("重发分组:" + x % seqnum + ";重发的是第" + x + "个包");
				} catch (Exception e) {
				}
			}
		};
		if (!flags[q]) {
			flags[q] = true;
		} else {
			executors[q].shutdown();
		}
		executors[q] = Executors.newSingleThreadScheduledExecutor();
		executors[q].scheduleWithFixedDelay(task, timeout, timeout, TimeUnit.SECONDS);
	}

	/**
	 * 结束计时
	 */
	public void TimeEnd(int q, int x) {
		if (flags[q]) {
			flags[q] = false;
			executors[q].shutdown();//终止计时器
		}
	}

	/**
	 * 读取文件，存入filestring和字节数组B中
	 * 
	 * @param filename
	 */
	public static void ReadFilebyLines(String filename) {
		filename = "src\\lab2\\"+filename;
	  File file = new File(filename);
		BufferedReader reader = null;
		try {
			reader = new BufferedReader(new FileReader(file));
			String tempstr = null;
			while ((tempstr = reader.readLine()) != null) {
				filestr = filestr + tempstr + "\r\n";
			}
			reader.close();
		//将所有数据转化为byte存到B数组中
			B = filestr.getBytes();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (reader != null) {
				try {
					reader.close();
				} catch (IOException e1) {
				}
			}
		}
	}

	/**
	 * 根据nextseqnum获得要传输的字节
	 * 
	 * @param nextseqnum 要传输的分组序号
	 *            
	 * @return 字节数组，要传输的字节
	 */
	public byte[] GetPart(int nextseqnum) {
		byte[] temp = new byte[1469];
		for (int i = 0; i < 1469; i++) {
			if (nextseqnum * 1469 + i >= B.length) {
				break;
			}
			temp[i] = B[nextseqnum * 1469 + i];
		}
		return temp;
	}

	/**
	 * 发送数据
	 */
	public void Send() {
		ReadFilebyLines("data.txt");
		team = (int) Math.ceil(B.length / 1469);
		try {
			SenderSocket = new DatagramSocket();
			ReceiveAckSocket = new DatagramSocket(ReceiveAckPort);
			while (true) {
				SendToReciver();
				ReceiveACK();
//				System.out.println("send_base=" + send_base);
				if (send_base >= team) {
					break;
				}
			}
		  //传输完毕后不要忘了关定时器
			for(int i = 0 ; i < N ; i++)
			{
			  executors[i].shutdown();
			}
			System.out.println("发送结束.");
		} catch (Exception e) {
		  System.out.println("发送结束.");
		}
	}

	/**
	 * 发送数据给接收方
	 */
	public void SendToReciver() {
		try {
			while (nextseqnum < send_base + N) {
				if (send_base >= team || nextseqnum >= team) {
					break;
				}
				byte[] tempb = GetPart(nextseqnum);
				String temp = new String(tempb);
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
				if (nextseqnum % 5 != 0) {
					SenderSocket.send(SendDataPacket);
					System.out.println("发送分组:" + nextseqnum % seqnum + ";发送的是第" + nextseqnum + "个包");
				} else {
					System.out.println("模拟分组" + nextseqnum % seqnum + "丢失" + ";丢失的是第" + nextseqnum + "个包");
				}
//				System.out.println("nextseqnum=" + nextseqnum);
//				System.out.println("send_base=" + send_base);
				TimeBegin(nextseqnum - send_base, nextseqnum);
				nextseqnum++;

			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * 接收ACK
	 * 
	 * @throws InterruptedException
	 */
	public void ReceiveACK() throws InterruptedException {
		try 
		{
			if (send_base >= team) 
			{
				return;
			}
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
			if ( times == 1 && ack == 15) 
      {
        System.out.println("模拟ACK" + ack + "丢失");
        times++;
      } 
			else
			{
  			System.out.println("接收到ACK" + ack%seqnum);
  			int a = ack, b = ack + seqnum;
  			//找到ACK信息代表的包号，通过包号建立起两个窗口序号的关系
  			while (!(send_base >= a && send_base <= b)) 
  			{
  				a += seqnum;
  				b += seqnum;
  			}
  			//判断是否接收到重复ACK（send_base之前的ACK不进行处理）
  			if (b - send_base > send_base - a) 
  			{
  				ack = a;
  			} 
  			else 
  			{
  				ack = b;
  			}
  //			System.out.println("ack=" + ack);
  			if (ack >= send_base && ack < send_base + N) 
  			{
  				ackarray[ack - send_base] = true;//表示收到ack。
  				TimeEnd(ack - send_base, ack);
  				if (ack == send_base) //窗口滑动
  				{
  					ackarray[0] = true;
  					int cnt = 0;
  					for (int i = 0; i < N; i++)
  					{
  						if (ackarray[i])
  						{
  							cnt++;
  						}
  						else 
  						{
  							break;
  						}
  					}
  					
  					for (int i = 0; i < N - cnt; i++) 
  					{
  						ackarray[i] = ackarray[i + cnt];
  						flags[i] = flags[i + cnt];
  						executors[i] = executors[i + cnt];
  					}
  					for (int i = N - cnt; i < N; i++) 
  					{
  						ackarray[i] = false;
  						executors[i] = null;
  						flags[i] = false;
  
  					}
  					System.out.println("窗口向后滑动" + cnt);
  					send_base = send_base + cnt;
  				}
  			}
			}
		} catch (IOException e) {
		}
		
		
		
	}

	

}

package lab2;

public class ACK {
  protected int ACK;
  protected String ack;
  protected byte[] ackByte;
  
  public ACK(int ACK) {
    this.ACK=ACK;
    ack=String.valueOf(ACK);
    ackByte=ack.getBytes();
  }
}

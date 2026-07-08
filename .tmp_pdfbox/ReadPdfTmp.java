import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.text.PDFTextStripper;
import java.io.File;
public class ReadPdfTmp {
  public static void main(String[] args) throws Exception {
    PDDocument document = PDDocument.load(new File(args[0]));
    PDFTextStripper stripper = new PDFTextStripper();
    stripper.setSortByPosition(true);
    String text = stripper.getText(document);
    document.close();
    System.out.println(text);
  }
}

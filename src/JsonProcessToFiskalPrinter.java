import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.shtrih.fiscalprinter.ShtrihFiscalPrinter;
import jpos.FiscalPrinter;
import jpos.JposException;

import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;

@Path("/toSFPrinter")
public class JsonProcessToFiskalPrinter {
    private static String companyName="";
    private static String companyAddress="";
    private static ShtrihFiscalPrinter printer;
    private static String comName = "ShtrihFptr";
    private static SimpleDateFormat df=new SimpleDateFormat("dd.MM.yyyy");
    private static SimpleDateFormat sdfCurrent=new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");
    private static String folder=new File("").getAbsolutePath() + File.separator + "logs";
    private static String logFile=folder+File.separator+df.format(System.currentTimeMillis())+".log";
    private static String error="";
    //инициализация наименования компании
    private static void setCompanyName() {
        companyName=companyAddress="";
        try {
            BufferedReader br = new BufferedReader(new FileReader("companyName.txt"));
            ArrayList<String> list = new ArrayList<>();
            String line = null;
            while ((line = br.readLine()) != null) {
                list.add(line);
            }
            br.close();
            if (list.size()>=1) {
                companyName=list.get(0);
            }
            else
                writeLogString("Ошибка инициализации названия компании (tomcat home folder - companyName.txt - 1 строка)");
            if (list.size()>=2) {
                companyAddress=list.get(1);
            }
            else
                writeLogString("Ошибка инициализации адреса компании (tomcat home folder - companyName.txt - 2 строка)");

        }
        catch (Exception e) {
            writeLogString("Ошибка инициализации названия и адреса компании (tomcat home folder - companyName.txt - 1,2 строка)");
        }
        companyName=setCoolString(companyName);
        companyAddress=setCoolString(companyAddress);
    }
    //уточняем размер для красивой печати
    private static String setCoolString(String s) {
        if (s.length()!=0) {
            while (s.length() > 33)
                s = s.substring(0, s.length() - 1);
            int razn = 33 - s.length();
            if (razn > 0) {
                if (razn % 2 != 0) razn--;  //чётное
                int num = razn / 2;
                String space = "";
                for (int i = 0; i < num; i++) space = space + " ";
                s = space + s + space;
            }
        }
        return s;
    }

    //запись лога
    private static void writeLogString(String aMsg) {
        error=aMsg;
        try {
            File f0 = new File(folder);
            if (!f0.exists()) f0.mkdir();
            File f=new File(logFile);
            if(!f.exists()) f.createNewFile();
            String line=null;
            try (
                    InputStream fis = new FileInputStream(logFile);
                    InputStreamReader isr = new InputStreamReader(fis);
                    BufferedReader br = new BufferedReader(isr);
            ) {
                line = br.readLine();
            }
            FileOutputStream fos=new FileOutputStream(logFile, true);
            byte[] b;
            if (line==null) b=("\r"+sdfCurrent.format(new java.util.Date()) +": "+aMsg).getBytes();
            else b=("\r\n"+sdfCurrent.format(new java.util.Date()) +": "+aMsg).getBytes();
            fos.write(b);
            fos.close();
        } catch (Exception e) {e.printStackTrace();}
    }

    //получаю объект класса
    private static Kkm getKkm(String json) {
        setCompanyName();
        writeLogString("Полученный json (" + json + ")");
        Gson gson = new GsonBuilder()
                .setPrettyPrinting()
                .create();
        Kkm k=null;
        try {
            k = gson.fromJson(json, Kkm.class);
        }
        catch (JsonSyntaxException e) {
            writeLogString("Не удалось распарсить json (" + e.getMessage() + ")");
        }
        return k;
    }

    //инициализирую ккм
    private static void initializePrinter() throws JposException {
        try {
            printer = new ShtrihFiscalPrinter(
                    new FiscalPrinter());
            printer.open(comName);
            printer.setPowerNotify(printer.JPOS_PN_ENABLED);
            FiscalPrinter p = new FiscalPrinter();
            printer.claim(0);
            printer.setDeviceEnabled(true);
            printer.resetPrinter();
            //
            printer.setHeaderLine(1,companyName,false);
            printer.setHeaderLine(2,companyAddress,false);
            //
        }
        catch (Exception e) {
            if (e.getMessage()!=null) writeLogString("ККМ не инициализирован (" + e.getMessage() + ")");
            else writeLogString("ККМ не инициализирован (" + e.toString() + ")");
        }
    }

    //печатаю длинные названия
    private static void printName(String name) throws JposException {
        try {
            while (name.length() >= 35) {
                printer.printRecMessage(name.substring(0, 35));
                name = name.substring(35);
            }
            printer.printRecMessage(name);
        }
        catch (Exception e) {
            writeLogString("Ошибка печати длинной позиции в чеке (" + e.getMessage() + ")");
        }
    }

    //задание имени кассира
    private static void SetKassir(String fio) {
        try {
            //если смена закрыта - по стандарту
            if (!printer.getDayOpened()) {
                if (fio!=null) {
                    printer.setPOSID("1", fio);
                    writeLogString("Задано имя кассира: '" + fio + "'");
                }
                else
                    writeLogString("Ошибка инициализации имени кассира: имя кассира null");
            }
            else {
                writeLogString("Смена не закрыта, имя кассира не меняем.");
            }
        }
        catch (Exception e) {
            if (fio!=null) writeLogString("Ошибка назначения ФИО кассира: '" + fio + "' (" + e.getMessage() + ")");
            else writeLogString("Ошибка назначения ФИО кассира: null (" + e.getMessage() + ")");
        }
    }

    //получаю json от медоса и печатаю чек продажи
    private static void Print(Kkm k) throws JposException, UnsupportedEncodingException {
        //
        SetKassir(k.getFIO());
        //
        //writeLogString("Начало печати");
        //printer.endFiscalReceipt(true);
        String paymentType="0";
        if (k.getIsTerminalPayment()) paymentType="20";
        try {
            printer.beginFiscalReceipt(true);
            //printer.printRecMessage(companyName);
            printer.printRecMessage("ДОБРО ПОЖАЛОВАТЬ!");
            //writeLogString("Открыт чек");
        }
        catch (Exception e) {
            writeLogString("Ошибка инициации печати чека продажи (" + e.getMessage() + ")");
        }
        /* Price - стоимость позиции в копейках
           Quantity - количество в граммах (1 штука = 1000) - надо бы проверить!
           vatInfo - номер налога - ?
           unitPrice - цена за единицу товара
           unitName - название единицы товара
           printZReport - Day end required
        * */
        for (Position p : k.getPos()) {
            //writeLogString("Начаты позиции");
            if (p != null) { //тупо-криво, но gson ><
                try {
                    printer.printRecItem(p.getCode(), (long) (p.getSum() * 100.0), p.getCount() * 1000, 0, (long) (100 * p.getPrice()), "шт.");
                    printName("(" + p.getName() + ")");
                    //writeLogString("Напечатана позиция");
                }
                catch (Exception e) {
                    writeLogString("Ошибка печати позиции в чеке продажи (" + e.getMessage() + ")");
                }
                try {
                    String str = "Ставка НДС " + (int) p.getTaxName() + "%        =" + p.getTaxSum();
                    while (str.length() <= 35) str = str.replace(" =", "  =");
                    if (p.getTaxName() != 0 && p.getTaxSum() != null) printer.printRecMessage(str);
                    //writeLogString("Напечатаны НДС");
                }
                catch (Exception e) {
                    writeLogString("Ошибка печати ставки НДС в чеке продажи (" + e.getMessage() + ")");
                }
            }
            else {
                writeLogString("Исключительная ситуация: пустой или некорректный json");
            }
        }
        try {
            printer.printRecMessage("------------------------------------");
            String str = "Итого НДС " + "                   =" + k.getTotalTaxSum();
            while (str.length() <= 35) str = str.replace(" =", "  =");
            printer.printRecMessage(str);
            //writeLogString("Напечатаны итого НДС");
        }
        catch (Exception e) {
            writeLogString("Ошибка печати ИТОГО в чеке продажи (" + e.getMessage() + ")");
        }
        try {
            //
            printer.printRecTotal((long) (k.getTotalPaymentSum() * 100.0), (long) (k.getTotalPaymentSum() * 100.0), paymentType);
            //writeLogString("Напечатано ИТОГО");
        }
        catch (Exception e) {
            if (e.getMessage().toString().contains("Неверное состояние"))
                writeLogString("ККМ в неверном состоянии! Возможно, общая сумма по услугам не равна вычисленной! (" + e.getMessage() + ")");
            try {
                printer.printRecMessage("ККМ в неверном состоянии!");
            }
            catch (Exception e0) {
                writeLogString("Не удалось вывести сообщение на ККМ (о неверном состоянии) (" + e0.getMessage() + ")");
            }
        }
        try {
            printer.endFiscalReceipt(false);
        }
        catch (Exception e) {
            writeLogString("Ошибка завершения чека продажи (" + e.getMessage() + ")");
        }
        // printer.cutPaper(0);
    }

    //получаю сумму от медоса и печатаю чек возврата
    private void printRefund(Kkm k) throws JposException {
        //
        SetKassir(k.getFIO());
        //
        String paymentType = "0";
        if (k.getIsTerminalPayment()) paymentType = "20";
        double toRefund = k.getTotalRefundSum();
        try {
            printer.beginFiscalReceipt(true);
            printer.printRecMessage(companyName);
        }
        catch (Exception e) {
            writeLogString("Ошибка инициации печати чека возврата (" + e.getMessage() + ")");
        }
        try {
            printer.printRecItemRefund("Все услуги", (long) (100.0 * toRefund), 1000, 0, (long) (100 * toRefund), "шт");
        }
        catch (Exception e) {
            writeLogString("Ошибка печати позиции в чеке возврата (" + e.getMessage() + ")");
        }
        try {
            printer.printRecTotal((long) (100.0 * toRefund), (long) (100.0 * toRefund), paymentType);
        }
        catch (Exception e) {
            writeLogString("Ошибка ИТОГО в чеке возврата (" + e.getMessage() + ")");
        }
        try {
            printer.endFiscalReceipt(false);
        }
        catch (Exception e) {
            writeLogString("Ошибка завершения чека возврата (" + e.getMessage() + ")");
        }
    }

    @POST
    @Produces("application/json;charset=Cp866")
    public Response answerMedOs(String data) throws JposException {
        try {
            System.out.println(data);
            Kkm k = getKkm(data);
            if (k!=null) {
                initializePrinter();
                switch (k.getFunction()) {
                    case "printXReport":
                        try {
                            printer.printRecMessage(companyName);
                            printer.printXReport();
                        }
                        catch (Exception e) {
                            writeLogString("Ошибка печати X-отчёта (" + e.getMessage() + ")");
                        }
                        break;
                    case "printZReport":
                        try {
                            printer.printRecMessage(companyName);
                            printer.printZReport();
                        }
                        catch (Exception e) {
                            writeLogString("Ошибка печати Z-отчёта (" + e.getMessage() + ")");
                        }
                        break;
                    case "makePayment":
                        try {
                            //подсчёт суммы
                            double sum=0;
                            for (Position p : k.getPos()) {
                                if (p != null)   sum+=p.getSum();
                                    double price=p.getPrice();
                                    if (p.getCount()*price!=p.getSum()) writeLogString("Сумма в позиции в чеке не равна рассчитанной (" + p.getCode() + ")");
                            }
                            if (sum!=k.getTotalPaymentSum())  writeLogString("Сумма по позициям в чеке не равна рассчитанной!");
                            //
                            Print(k);
                        }
                        catch (Exception e) {
                            writeLogString("Ошибка печати чека продажи (" + e.getMessage() + ")");
                        }
                        break;
                    case "makeRefund":
                        try {
                            printRefund(k);
                        }
                        catch (Exception e) {
                            writeLogString("Ошибка печати чека возврата (" + e.getMessage() + ")");
                        }
                        break;
                    case "continuePrint":
                        try {
                            byte[] tx = "B0h".getBytes();  //команда возобновления печати при обрыве ленты посередине чека
                            printer.executeCommand(tx,0);
                        }
                        catch (Exception e) {
                            if (e.getMessage()!=null) writeLogString("getState: " + printer.getState() + ". Не удалось возобновить печать! (" + e.getMessage() + ")");
                            else writeLogString("getState: " + printer.getState() + ". Не удалось возобновить печать! (" + e.toString() + ")");
                        }
                        break;
                    default:
                        writeLogString("Была получена недекларированная команда (" + k.getFunction() + ")");
                        break;
                }
            }
        } catch (Exception e0) {
            writeLogString("Исключительная ситуация (" + e0.getMessage() + ")");
        }
        if (error.equals(""))  return Response.status(Response.Status.OK).build();
        else {
            if (error.toString().contains("Day end")) {
                printer.printRecMessage("24 часа истекли, нужен Z-отчёт");
                initializePrinter();
            }
            if (error.contains("Не хватает наличности")) {
                printer.printRecMessage("Не хватает наличности в кассе");
                initializePrinter();
            }
            return Response.status(Response.Status.SEE_OTHER).entity(error).build();
        }
    }
}
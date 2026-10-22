package com.marketsniper.app;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Typeface;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import android.text.InputType;
import java.util.*;
import java.io.*;
import org.json.*;

public class MainActivity extends Activity {
    LinearLayout root, results;
    EditText serverInput, roiInput, feeInput;
    TextView status;
    ArrayList<Offer> offers = new ArrayList<>();

    static class Offer {
        String server, item, stall, side, updated;
        double price;
        Offer(String s, String i, String st, double p, String sd, String u) {
            server=s; item=i; stall=st; price=p; side=sd; updated=u;
        }
    }

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        buildUi();
        loadDemo();
        renderDeals();
    }

    int dp(float x) { return (int)(x * getResources().getDisplayMetrics().density); }

    TextView text(String s, int size, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextSize(size); t.setTextColor(Color.rgb(28,34,42));
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        t.setPadding(0, dp(4), 0, dp(4));
        return t;
    }

    void buildUi() {
        ScrollView scroll = new ScrollView(this);
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(20), dp(18), dp(20));
        root.setBackgroundColor(Color.rgb(246,248,251));
        scroll.addView(root);
        setContentView(scroll);

        root.addView(text("MARKET SNIPER", 25, true));
        root.addView(text("Arizona RP • аналитика рынка", 14, false));
        root.addView(text("Данные ниже демонстрационные. Реальный источник цен ещё не подключён.", 13, true));

        root.addView(text("Сервер", 14, true));
        serverInput = new EditText(this); serverInput.setSingleLine(true);
        serverInput.setText("Mobile 3"); serverInput.setHint("Например, Mobile 3");
        root.addView(serverInput);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout c1 = new LinearLayout(this); c1.setOrientation(LinearLayout.VERTICAL);
        LinearLayout c2 = new LinearLayout(this); c2.setOrientation(LinearLayout.VERTICAL);
        c1.addView(text("Минимальная прибыль, %", 12, true));
        roiInput = new EditText(this); roiInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL); roiInput.setText("10");
        c1.addView(roiInput);
        c2.addView(text("Комиссия, %", 12, true));
        feeInput = new EditText(this); feeInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL); feeInput.setText("0");
        c2.addView(feeInput);
        row.addView(c1, new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(c2, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(row);

        Button importButton = new Button(this);
        importButton.setText("Импортировать CSV с ценами");
        importButton.setOnClickListener(v -> pickCsv());
        root.addView(importButton);

        Button refresh = new Button(this);
        refresh.setText("Найти выгодные предложения");
        refresh.setOnClickListener(v -> renderDeals());
        root.addView(refresh);

        status = text("", 13, false); root.addView(status);
        root.addView(text("Найденные предложения", 20, true));
        results = new LinearLayout(this); results.setOrientation(LinearLayout.VERTICAL);
        root.addView(results);
        root.addView(text("Покупку и продажу подтверждай вручную. Цена может измениться до проверки лавки.", 12, false));
    }

    void loadDemo() {
        offers.clear();
        String s = "Mobile 3";
        offers.add(new Offer(s, "Аптечка", "18", 100000, "sell", "Демо"));
        offers.add(new Offer(s, "Аптечка", "7", 125000, "buy", "Демо"));
        offers.add(new Offer(s, "Кейс", "15", 500000, "sell", "Демо"));
        offers.add(new Offer(s, "Кейс", "21", 590000, "buy", "Демо"));
        offers.add(new Offer(s, "Материалы", "12", 75000, "sell", "Демо"));
        offers.add(new Offer(s, "Материалы", "9", 82000, "buy", "Демо"));
    }

    void pickCsv() {
        android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(android.content.Intent.CATEGORY_OPENABLE);
        intent.setType("text/*");
        try { startActivityForResult(intent, 41); }
        catch(Exception e) { status.setText("Не удалось открыть выбор файла."); }
    }

    @Override protected void onActivityResult(int req, int res, android.content.Intent data) {
        super.onActivityResult(req, res, data);
        if(req != 41 || res != RESULT_OK || data == null) return;
        try {
            InputStream is = getContentResolver().openInputStream(data.getData());
            BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"));
            String header = br.readLine();
            if(header == null) throw new Exception("Пустой CSV");
            String[] heads = header.replace("\uFEFF","").split(",");
            Map<String,Integer> ix = new HashMap<>();
            for(int i=0;i<heads.length;i++) ix.put(norm(heads[i]), i);
            int itemI = find(ix,"item","товар","предмет","name","название");
            int priceI = find(ix,"price","цена","стоимость");
            int sideI = find(ix,"side","type","тип","сторона");
            int serverI = find(ix,"server","сервер");
            int stallI = find(ix,"stall","shop","лавка","номер лавки","номер_лавки");
            int updateI = find(ix,"updated","timestamp","time","обновлено","время");
            if(itemI<0 || priceI<0 || sideI<0) throw new Exception("Нужны колонки item/товар, price/цена, side/тип");
            ArrayList<Offer> loaded = new ArrayList<>();
            String line;
            while((line=br.readLine())!=null) {
                String[] a = line.split(",", -1);
                String item=get(a,itemI), side=canonical(get(a,sideI));
                String server=serverI<0?serverInput.getText().toString():get(a,serverI);
                String stall=stallI<0?"?":get(a,stallI);
                String updated=updateI<0?"Не указано":get(a,updateI);
                double price=parsePrice(get(a,priceI));
                if(item.length()>0 && price>0 && side.length()>0)
                    loaded.add(new Offer(server,item,stall,price,side,updated));
            }
            br.close();
            offers.clear(); offers.addAll(loaded);
            status.setText("Импортировано предложений: "+offers.size()+". Источник: выбранный CSV.");
            renderDeals();
        } catch(Exception e) {
            status.setText("Ошибка CSV: "+e.getMessage());
        }
    }

    int find(Map<String,Integer> ix, String... names) {
        for(String n:names) if(ix.containsKey(norm(n))) return ix.get(norm(n));
        return -1;
    }
    String norm(String s) { return s.trim().toLowerCase(Locale.ROOT).replace("_"," ").replace("\uFEFF",""); }
    String get(String[] a,int i) { return i>=0 && i<a.length ? a[i].trim().replace("\"","") : ""; }
    String canonical(String s) {
        s=norm(s);
        if(s.equals("sell") || s.contains("прода")) return "sell";
        if(s.equals("buy") || s.contains("скуп") || s.contains("покупает")) return "buy";
        return "";
    }
    double parsePrice(String s) {
        try {
            s=s.trim().toLowerCase(Locale.ROOT).replace(" ","").replace(",","");
            if(s.endsWith("кк") || s.endsWith("kk")) return Double.parseDouble(s.substring(0,s.length()-2))*1000000;
            if(s.endsWith("к") || s.endsWith("k")) return Double.parseDouble(s.substring(0,s.length()-1))*1000;
            return Double.parseDouble(s);
        } catch(Exception e) { return -1; }
    }
    String money(double x) { return String.format(Locale.US, "%,.0f", x).replace(",", " "); }

    void renderDeals() {
        results.removeAllViews();
        String server = serverInput.getText().toString().trim();
        double minRoi=10, fee=0;
        try { minRoi=Double.parseDouble(roiInput.getText().toString()); } catch(Exception ignored) {}
        try { fee=Double.parseDouble(feeInput.getText().toString()); } catch(Exception ignored) {}
        Map<String,List<Offer>> grouped = new LinkedHashMap<>();
        for(Offer o:offers) {
            if(!o.server.equalsIgnoreCase(server)) continue;
            grouped.computeIfAbsent(norm(o.item), k -> new ArrayList<>()).add(o);
        }
        ArrayList<String> cards = new ArrayList<>();
        for(List<Offer> list:grouped.values()) {
            Offer cheap=null, buyer=null;
            for(Offer o:list) {
                if(o.side.equals("sell") && (cheap==null || o.price<cheap.price)) cheap=o;
                if(o.side.equals("buy") && (buyer==null || o.price>buyer.price)) buyer=o;
            }
            if(cheap==null || buyer==null) continue;
            double net=buyer.price*(1-fee/100.0), profit=net-cheap.price, roi=profit/cheap.price*100.0;
            if(roi>=minRoi) {
                String name=cheap.item;
                cards.add(name+"\nКупить: $"+money(cheap.price)+" • лавка №"+cheap.stall+
                    "\nСкупка: $"+money(buyer.price)+" • лавка №"+buyer.stall+
                    "\nЧистая прибыль: $"+money(profit)+" • доходность "+String.format(Locale.US,"%.1f",roi)+"%"+
                    "\nОбновление: "+cheap.updated+" / "+buyer.updated);
            }
        }
        status.setText("Предложений в наборе данных: "+offers.size()+" • подходящих сделок: "+cards.size());
        if(cards.isEmpty()) {
            results.addView(text("Подходящих сделок нет. Импортируй данные с предложениями sell и buy.",14,false));
            return;
        }
        for(String c:cards) {
            TextView card=text(c,15,false);
            card.setBackgroundColor(Color.WHITE);
            card.setPadding(dp(12),dp(12),dp(12),dp(12));
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
            lp.setMargins(0,0,0,dp(10));
            results.addView(card,lp);
        }
    }
}

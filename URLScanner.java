package wework;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.net.*;
import java.util.regex.*;
import java.util.Scanner;
import java.nio.charset.StandardCharsets;
import java.io.FileWriter;
import java.io.BufferedWriter;

class URLSearch implements Runnable {
    // indicate next URL to search
    volatile int index;
    // array of all URLs
    volatile ArrayList<String> urls;
    // array of URLs which have match of the search
    volatile ArrayList<String> result;
    // array of URLs which have error
    volatile ArrayList<String> errors;
    // search words
    Pattern searchWord;

    URLSearch(Pattern p, ArrayList<String> urlList, ArrayList<String> resultList, ArrayList<String> exceptionList) {
        index = 0;
        urls = urlList;
        result = resultList;
        searchWord = p;
        errors = exceptionList;
    }

    synchronized String getNextUrl() {
        if (index % 50 == 0 || index == urls.size())
            System.out.println("Processing:" + index + "/" + urls.size());

        if (index >= urls.size()) {
            ++index;
            return "";
        } else
            return urls.get(index++);
    }

    synchronized void addResult(String s) {
        result.add(s);
    }

    synchronized void addException(String s) {
        errors.add(s);
    }

    public static String getURLString(String url) {
        Scanner scanner = null;
        try {
            URL target = new URL(url);
            URLConnection conn = target.openConnection();
            conn.setRequestProperty("User-Agent",
                    "Mozilla/5.0 (Windows NT 6.1; WOW64; rv:25.0) Gecko/20100101 Firefox/25.0");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(10000);
            scanner = new Scanner(conn.getInputStream(), StandardCharsets.UTF_8.toString());

            scanner.useDelimiter("\\A");
            if (!scanner.hasNext())
                return "";
            else
                return scanner.next();
        } catch (Exception e) {
            return "";
        } finally {
            if (scanner != null)
                scanner.close();
        }

    }

    // return true if pattern found in URL content
    // patch the http or https header
    public boolean searchURL(String urlIn, Pattern p) {
        ArrayList<String> urls = new ArrayList<String>();
        if (urlIn.startsWith("http:") || urlIn.startsWith("https:")) {
            urls.add(urlIn);
        } else {
            urls.add("http://www." + urlIn);
            urls.add("https://www." + urlIn);
        }

        for (int i = 0; i < urls.size(); ++i) {
            String url = urls.get(i);
            String s = getURLString(url);
            if (s == "") {
                if (i == urls.size() - 1)
                    addException(urlIn);
                continue;
            }
            Matcher m = p.matcher(s);
            if (m.find()) {
                return true;
            } else {
                return false;
            }
        }
        return false;
    }

    public void run() {
        while (true) {
            String url = getNextUrl();
            if (url == "")
                break;
            if (searchURL(url, searchWord))
                addResult(url);
        }
    }
}

public class URLScanner {

    public static final int THREAD_NUM = 20;

    // return 0 for success, other numbers are error code
    public static int getURLList(String fileName, ArrayList<String> result) {
        BufferedReader bufferedReader = null;
        try {
            try {
                File file = new File(fileName);
                FileReader fileReader = new FileReader(file);
                bufferedReader = new BufferedReader(fileReader);
                // skip the first line
                bufferedReader.readLine();
                String line;
                while ((line = bufferedReader.readLine()) != null) {
                    String[] items = line.split(",");
                    if (items.length > 1) {
                        String url = items[1].replace("\"", "");
                        result.add(url);
                    }
                }

            } finally {
                if (bufferedReader != null)
                    bufferedReader.close();
            }
        } catch (Exception e) {
            System.out.println(e);
            return 1;
        }
        return 0;
    }

    public static void main(String[] args) {

        // Validate the arguments
        if (args.length == 0) {
            System.out.println("No Input File");
            return;
        }

        if (args.length == 1) {
            System.out.println("No Search Pattern");
            return;
        }

        // read the URL file
        ArrayList<String> urls = new ArrayList<String>();
        if (getURLList(args[0], urls) != 0) {
            System.out.println("Error Reading File");
            return;
        }
        if (urls.size() == 0) {
            System.out.println("No URL found");
            return;
        }

        // Start the threads
        ArrayList<String> results = new ArrayList<String>();
        ArrayList<String> errors = new ArrayList<String>();
        Pattern pattern = Pattern.compile(args[1]);
        URLSearch searchThread = new URLSearch(pattern, urls, results, errors);
        ArrayList<Thread> threadPool = new ArrayList<Thread>();

        for (int i = 0; i < THREAD_NUM; ++i) {
            threadPool.add(new Thread(searchThread));
            threadPool.get(i).start();
        }

        // Waiting for thread to finish
        for (int i = 0; i < THREAD_NUM; ++i) {
            try {
                threadPool.get(i).join();
            } catch (Exception e) {

            }
        }

        // Write result and error
        BufferedWriter resultWriter = null;
        BufferedWriter errorWriter = null;
        try {
            resultWriter = new BufferedWriter(new FileWriter("results.txt"));
            for (String s : results)
                resultWriter.write(s + "\n");
            errorWriter = new BufferedWriter(new FileWriter("errors.txt"));
            for (String s : errors)
                errorWriter.write(s + "\n");

        } catch (Exception e) {
        } finally {
            try {
                if (resultWriter != null)
                    resultWriter.close();
                if (errorWriter != null)
                    errorWriter.close();
            } catch (Exception e) {
            }
        }

        System.out.println("Finished");
        System.out.println("Total URL: " + urls.size());
        System.out.println("URL Match: " + results.size());
        System.out.println("URL Error: " + errors.size());
        System.out.println("Please find result and error in results.txt and errors.txt");

    }

}

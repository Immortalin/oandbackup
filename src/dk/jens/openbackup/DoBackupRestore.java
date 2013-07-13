package dk.jens.openbackup;

import android.os.Environment;
import android.os.Build;
import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class DoBackupRestore
{
    final static String TAG = OBackup.TAG; 
    Process p;
    DataOutputStream dos;
    public void doBackup(File backupDir, String packageData, String packageApk)
    {
        Log.i(TAG, "doBackup: " + packageData);
        try
        {
            p = Runtime.getRuntime().exec("su");
            dos = new DataOutputStream(p.getOutputStream());
            // /lib kan give nogle mærkelige problemer, og er alligevel pakket med apken
            dos.writeBytes("rsync -r --exclude=/lib " + packageData + " " + backupDir.getAbsolutePath() + "\n");
            dos.flush();
            dos.writeBytes("cp " + packageApk + " " + backupDir.getAbsolutePath() + "\n");
            dos.flush();
            dos.writeBytes("exit\n");
            dos.flush();

            // hvis waitFor() ikke giver nogen return:
            //http://stackoverflow.com/questions/5483830/process-waitfor-never-returns
            //http://www.javaworld.com/javaworld/jw-12-2000/jw-1229-traps.html
/*
            InputStream stderr = p.getInputStream();
            InputStreamReader isr = new InputStreamReader(stderr);
            BufferedReader br = new BufferedReader(isr);
            String line = null;
            while((line = br.readLine()) != null)
            {
                Log.i(TAG, line);
            }
*/

            int retval = p.waitFor();
            Log.i(TAG, "return: " + retval);
            if(retval != 0)
            {
                ArrayList<String> stderr = getOutput(p).get("stderr");
                for(String line : stderr)
                {
                    writeErrorLog(line);
                }
            }
        }
        catch(IOException e)
        {
            Log.i(TAG, e.toString());
        }
        catch(InterruptedException e)
        {
            Log.i(TAG, e.toString());
        }
    }
    public void doRestore(File backupDir, String packageName)
    {
        String packageData = ""; // TODO: tjek om packageData får en meningsfuld værdi 
        String packageApk = ""; 
        ArrayList<String> logLines = readLogFile(backupDir, packageName);
//        packageData = "/data/data/" + logLines.get(2); // midlertidig indtil logfilerne er skrevet ordentligt
        packageApk = logLines.get(3);
        packageData = logLines.get(4);
        String[] apk = packageApk.split("/");
        packageApk = apk[apk.length - 1];
        Log.i(TAG, "doRestore: " + packageData + " : " + packageApk);

        try
        {
            p = Runtime.getRuntime().exec("su");
            dos = new DataOutputStream(p.getOutputStream());
            dos.writeBytes("cp -r " + backupDir.getAbsolutePath() + "/" + packageName + "/* " + packageData + "\n");
/*
            dos.writeBytes("am force-stop " + packageName + "\n");
            dos.flush();
            dos.writeBytes("rsync -r --exclude=/lib " + backupDir.getAbsolutePath() + "/" + packageName + "/* " + packageData + "\n");
*/
            dos.flush();
            dos.writeBytes("exit\n");
            dos.flush();

            // hvis waitFor() ikke giver nogen return:
            //http://stackoverflow.com/questions/5483830/process-waitfor-never-returns
            //http://www.javaworld.com/javaworld/jw-12-2000/jw-1229-traps.html
/*
            InputStream stderr = p.getInputStream();
            InputStreamReader isr = new InputStreamReader(stderr);
            BufferedReader br = new BufferedReader(isr);
            String line = null;
            while((line = br.readLine()) != null)
            {
                Log.i(TAG, line);
            }
*/
            int retval = p.waitFor();
            if(retval != 0)
            {
                ArrayList<String> stderr = getOutput(p).get("stderr");
                for(String line : stderr)
                {
                    writeErrorLog(line);
                }
            }
            Log.i(TAG, "return: " + retval);
        }
        catch(IOException e)
        {
            Log.i(TAG, e.toString());
        }
        catch(InterruptedException e)
        {
            Log.i(TAG, e.toString());
        }
    }
    public void setPermissions(String packageDir)
    {
        try
        {
            // busybox location
            String busybox;
            if(android.os.Build.MODEL.equals("sdk")) // tjekke for emulator -> erstat med rigtigt tjek efter busybox
            {
                busybox = "/data/busybox/";
            }
            else
            {
                busybox = "/system/xbin/";
            }
            String chown = busybox + "chown";
            String chmod = busybox + "chmod";
            String awk = busybox + "awk";
            String stat = busybox + "stat";
            String sed = busybox + "sed";
            Process p = Runtime.getRuntime().exec("sh"); // man behøver vist ikke su til stat - det gør man til ls -l /data/
            DataOutputStream dos = new DataOutputStream(p.getOutputStream());
            //uid:
//            dos.writeBytes("ls -l /data/data/ | grep " + packageDir + " | " + awk + " '{print $2}'" + "\n");
            dos.writeBytes(stat + " " + packageDir + " | grep Uid | " + awk + " '{print $4}' | " + sed + " -e 's/\\///g' -e 's/(//g'\n");
            dos.flush();
            //gid:
//            dos.writeBytes("ls -l /data/data/ | grep " + packageDir + " | " + awk + " '{print $3}'" + "\n"); 
            dos.writeBytes(stat + " " + packageDir + " | grep Gid |" + awk + " '{print $4}' | " + sed + " -e 's/\\///g' -e 's/(//g'\n");
            dos.flush();

            dos.writeBytes("exit\n");
            dos.flush();
            int ret = p.waitFor();
          
            Log.i(TAG, "setPermissions return 1: " + ret);

            InputStreamReader isr = new InputStreamReader(p.getInputStream());
            BufferedReader stdin = new BufferedReader(isr);
            String line;
            ArrayList<String> uid_gid = new ArrayList<String>();
            while((line = stdin.readLine()) != null)
            {
                // tjek om man faktisk får noget brugbart 
                uid_gid.add(line);
//                Log.i(TAG, "uid_gid: " + line);
            }
            if(!uid_gid.isEmpty())
            {

                p = Runtime.getRuntime().exec("su");
                dos = new DataOutputStream(p.getOutputStream());
                dos.writeBytes(chown + " -R " + uid_gid.get(0) + ":" + uid_gid.get(1) + " " + packageDir + "\n");
                dos.flush();
                dos.writeBytes(chmod + " -R 755 " + packageDir + "\n");
                // midlertidig indtil mere detaljeret som i fix_permissions l.367
                dos.flush();
                dos.writeBytes("exit\n");
                dos.flush();
                ret = p.waitFor();
                Log.i(TAG, "setPermissions return 2: " + ret);

                if(ret != 0)
                {
                    ArrayList<String> output = getOutput(p).get("stderr");
                    for(String outLine : output)
                    {
                        writeErrorLog(outLine);
                        Log.i(TAG, outLine);
                    }
                }
            }
            else
            {
                writeErrorLog("setPermissions error: could not find permissions for " + packageDir);
            }
        }
        catch(IOException e)
        {
            Log.i(TAG, e.toString());
        }
        catch(InterruptedException e)
        {
            Log.i(TAG, e.toString());
        }                   
    }
    public int restoreApk(File backupDir, String apk) 
    {
        File checkDataPath = new File("/data/app/" + apk);
        if(!checkDataPath.exists())
        {
            try
            {
                p = Runtime.getRuntime().exec("su");
                dos = new DataOutputStream(p.getOutputStream());
                dos.writeBytes("pm install " + backupDir.getAbsolutePath() + "/" + apk + "\n");
                dos.flush();
                dos.writeBytes("exit\n");
                dos.flush();
                int ret = p.waitFor();
                Log.i(TAG, "restoreApk return: " + ret);
                // det ser ud til at pm install giver 0 som return selvom der sker en fejl
                if(ret != 0)
                {
                    ArrayList<String> err = getOutput(p).get("stderr");
                    for(String line : err)
                    {
                        writeErrorLog(line);
                    }
                }
                return ret;
            }
            catch(IOException e)
            {
                Log.i(TAG, e.toString());
                return 1;
            }
            catch(InterruptedException e)
            {
                Log.i(TAG, e.toString());
                return 1;
            }           
        }
        else
        {
            return 1;
        }
    }
    public int uninstall(String packageName)
    {
        try
        {
            p = Runtime.getRuntime().exec("su");
            dos = new DataOutputStream(p.getOutputStream());
            // tjek med File.exists() ser ikke ud til at virke
            dos.writeBytes("pm uninstall " + packageName + "\n");
            dos.flush();
            dos.writeBytes("rm -r /data/data/" + packageName + "\n");
            dos.flush();
            dos.writeBytes("rm -r /data/app-lib/" + packageName + "*\n");
            dos.flush();
            // pm uninstall sletter ikke altid mapper og lib-filer ordentligt.
            // indføre tjek på pm uninstalls return 
            dos.writeBytes("exit\n");
            dos.flush();
            int ret = p.waitFor();
            if(ret != 0)
            {
                ArrayList<String> err = getOutput(p).get("stderr");
                for(String line : err)
                {
                    if(!line.contains("No such file or directory"))
                    {
                        writeErrorLog(line);
                        Log.i(TAG, "uninstall return: " + ret);
                    }
                }
            }
            return ret;
        }
        catch(IOException e)
        {
            Log.i(TAG, e.toString());
            return 1;
        }
        catch(InterruptedException e)
        {
            Log.i(TAG, e.toString());
            return 1;
        }           
    }
    public void deleteBackup(File file)
    {
        if(file.exists())
        {
//            Log.i(TAG, "deleting backup: " + file.getAbsolutePath());
            if(file.isDirectory())
            {
                if(file.list().length > 0)
                {
                    for(File child : file.listFiles())
                    {
                        deleteBackup(child);
                    }
                    if(file.list().length == 0)
                    {
                        file.delete();
                    }
                }
                else
                {
                    file.delete();
                }
            }
            else
            {
                file.delete();
            }
        }
    }
    public ArrayList<String> readLogFile(File backupDir, String packageName)
    {
        ArrayList<String> logLines = new ArrayList<String>();
        try
        {
            File logFile = new File(backupDir.getAbsolutePath() + "/" + packageName + ".log");
            FileReader fr = new FileReader(logFile);
            BufferedReader breader = new BufferedReader(fr);
            String logLine;
            while((logLine = breader.readLine()) != null)
            {
                logLines.add(logLine);
//                Log.i(TAG, logLine);
            }
            return logLines;
        }
        catch(FileNotFoundException e)
        {
//            Log.i(TAG, e.toString());
            return logLines;
        }
        catch(IOException e)
        {
            Log.i(TAG, e.toString());
            return logLines;
        }
    }
    public void writeLogFile(String filePath, String content)
    {
        Date date = new Date();
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy - HH:mm:ss");
        String dateFormated = dateFormat.format(date);
        content = content + "\n" + dateFormated + "\n";
        try
        {
            File outFile = new File(filePath);
            outFile.createNewFile();
		    FileWriter fw = new FileWriter(outFile.getAbsoluteFile());
		    BufferedWriter bw = new BufferedWriter(fw);
            bw.write(content);
            bw.close();        
        }
        catch(IOException e)
        {
            Log.i(TAG, e.toString());
        }
    }
    public Map<String, ArrayList<String>> getOutput(Process p)
    {
        ArrayList<String> out = new ArrayList<String>();
        ArrayList<String> err = new ArrayList<String>();
        try
        {
            InputStreamReader isr = new InputStreamReader(p.getInputStream());
            BufferedReader stdout = new BufferedReader(isr);
            String line;
            while((line = stdout.readLine()) != null)
            {
                out.add(line);
            }
            isr = new InputStreamReader(p.getErrorStream());
            BufferedReader stderr = new BufferedReader(isr);
            while((line = stderr.readLine()) != null)
            {
                err.add(line);
//                Log.i(TAG, "error: " + line);
            }
            Map<String, ArrayList<String>> map = new HashMap();
            map.put("stdout", out);
            map.put("stderr", err);
            return map;
        }
        catch(IOException e)
        {
            Map<String, ArrayList<String>> map = new HashMap();
            Log.i(TAG, e.toString());
            out.add(e.toString());
            map.put("stdout", out);
            return map;
        }
    }
    public void writeErrorLog(String err)
    {
        // TODO: brugbare informationer om hvilken pakke og hvilken fejl, der opstod
        try
        {
            File outFile = new File(Environment.getExternalStorageDirectory() + "/obackup.log");
            if(!outFile.exists())
            {
                outFile.createNewFile();
            }
		    FileWriter fw = new FileWriter(outFile.getAbsoluteFile(), true); // true: append
		    BufferedWriter bw = new BufferedWriter(fw);
            bw.write(err + "\n");
            bw.close();        
        }
        catch(IOException e)
        {
            Log.i(TAG, e.toString());
        }
    }
    public boolean checkSuperUser()
    {
        try
        {
            p = Runtime.getRuntime().exec("su");
            dos = new DataOutputStream(p.getOutputStream());
//            dos.writeBytes("echo hello\n");
            dos.writeBytes("exit\n");
            dos.flush();
            p.waitFor();
            if(p.exitValue() != 0)
            {
                return false;
            }
            else
            {
                return true;
            }
        }
        catch(IOException e)
        {
            Log.i(TAG, e.toString());
            return false;
        }
        catch(InterruptedException e)
        {
            Log.i(TAG, e.toString());
            return false;
        }
    }
    public boolean checkRsync()
    {
        try
        {
            p = Runtime.getRuntime().exec("sh");
            dos = new DataOutputStream(p.getOutputStream());
            dos.writeBytes("rsync\n");
            dos.writeBytes("exit\n");
            dos.flush();
            int rsyncReturn = p.waitFor();
            if(rsyncReturn == 1)
            {
                return true;
            }
            else
            {
                ArrayList<String> stderr = getOutput(p).get("stderr");
                for(String line : stderr)
                {
                    writeErrorLog(line);
                }
                return false;
            }
//            Log.i(TAG, "rsyncReturn: " + rsyncReturn);
        }
        catch(IOException e)
        {
            Log.i(TAG, e.toString());
            return false;
        }
        catch(InterruptedException e)
        {
            Log.i(TAG, e.toString());
            return false;
        }
    }
    public boolean checkBusybox()
    {
        try
        {
            p = Runtime.getRuntime().exec("sh");
            dos = new DataOutputStream(p.getOutputStream());
            dos.writeBytes("busybox\n");
            dos.writeBytes("exit\n");
            dos.flush();
            int bboxReturn = p.waitFor();
            if(bboxReturn == 0)
            {
                return true;
            }
            else
            {
                ArrayList<String> stderr = getOutput(p).get("stderr");
                for(String line : stderr)
                {
                    writeErrorLog(line);
                }
                return false;
            }
//            Log.i(TAG, "busyboxReturn: " + bboxReturn);
        }
        catch(IOException e)
        {
            Log.i(TAG, e.toString());
            return false;
        }
        catch(InterruptedException e)
        {
            Log.i(TAG, e.toString());
            return false;
        }
    }

}
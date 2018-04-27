package gzq.evaluation;

import gzq.source.ClassName;
import org.omg.CORBA.INTERNAL;
import utils.Utility;
import gzq.source.CodeCorpus_SpiltCorpus;
import gzq.source.LenScore_OriginClass;

import java.io.*;
import java.util.*;

public class Evaluation {
    //本过程读取的数据文件列表
    public static String ClassNameFileName = "ClassName.txt";
    public static String MethodNameFileName = "MethodName.txt";
    public static String VSMScoreFileName = "VSMScore.txt";
    public static String SimiScoreFileName = "SimiScore.txt";
    public static String LengthScoreFileName = "LengthScore.txt";
    public static String LOCFileName = "LOC.txt";
    public static String ImportFileName = "Import.txt";

    public static Integer a = CodeCorpus_SpiltCorpus.spiltclass;
    public static Integer b = LenScore_OriginClass.B;
    public static float alpha = 0.3f;

    public static Hashtable<String, Integer> idTable = null;
    public static Hashtable<Integer, String> nameTable = null;

    public static Hashtable<String, Integer> methodIdTable = null;
    public static Hashtable<Integer, String> methodNameTable = null;

    public static Hashtable<Integer, TreeSet<String>> fixTable = null;
    public static Hashtable<String, Double> lenTable = null;

    public static Hashtable<String, Integer> LOCTable = null;
    public static HashMap<Integer, String> bugNameTable = null;
    public static HashMap<String, HashSet<String>> shortNameSet = null;
    public static LinkedList<HashMap<String, Integer>> groups = null;
    public static Iterator<HashMap<String, Integer>> itr = null;
    public static HashMap<String, Integer> tmp_group = null;
    public static Integer TotalLOC;

    public static void init() throws IOException {
        idTable = Utility.getFileIdTable(ClassNameFileName);
        nameTable = Utility.getFileNameTable(ClassNameFileName);

        methodIdTable = Utility.getFileIdTable(MethodNameFileName);
        methodNameTable = Utility.getFileNameTable(MethodNameFileName);

        fixTable = Utility.getFixedTable();
        lenTable = Utility.getLenScore(LengthScoreFileName);

        LOCTable = getLOC(LOCFileName);
        shortNameSet = getShortNameSet();
        bugNameTable = getBugNameSet();
        groups = new LinkedList<>();
    }

    public static Hashtable<String, Integer> getLOC(String fileName) throws IOException {
        TotalLOC = new Integer(0);
        Hashtable<String, Integer> table = new Hashtable<>();
        BufferedReader reader = new BufferedReader(new FileReader(Utility.outputFileDir + fileName));
        String line;
        while ((line = reader.readLine()) != null) {
            String[] values = line.split("\t");
            Integer loc = Integer.parseInt(values[1]);
            TotalLOC += loc;
            String nameString = values[0].trim();
            table.put(nameString, loc);
        }
        System.out.println("Total LOC: " + TotalLOC);
        return table;
    }

    private static Rank[] sort(float[] finalR) {
        Rank[] R = new Rank[finalR.length];
        for (int i = 0; i < R.length; i++) {
            Rank rank = new Rank();
            rank.rank = finalR[i];
            rank.id = i;
            R[i] = rank;
        }
        R = insertionSort(R);
        return R;
    }

    private static Rank[] insertionSort(Rank[] R) {
        for (int i = 0; i < R.length; i++) {
            int maxIndex = i;
            for (int j = i; j < R.length; j++)
                if (R[j].rank > R[maxIndex].rank)
                    maxIndex = j;
            Rank tmpRank = R[i];
            R[i] = R[maxIndex];
            R[maxIndex] = tmpRank;
        }
        return R;
    }

    public static float[] combine(float[] vsmVector, float[] graphVector, float f) {
        float[] results = new float[Utility.sourceFileCount];
        for (int i = 0; i < Utility.sourceFileCount; i++)
            results[i] = vsmVector[i] * (1 - f) + graphVector[i] * f;
        return results;
    }

    /**
     * 获取短文件名xxx.java
     *
     * @return
     * @throws IOException
     */
    public static HashMap<String, HashSet<String>> getShortNameSet() throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(Utility.outputFileDir + "ClassName.txt"));
        HashMap<String, HashSet<String>> nameSet = new HashMap<>();
        String line;
        while ((line = reader.readLine()) != null) {
            String[] fields = line.split("\t");
            String tmp = fields[1].substring(0, fields[1].lastIndexOf("."));
            String name = tmp.substring(tmp.lastIndexOf(".") + 1) + ".java";

            if (!nameSet.containsKey(name)) {
                HashSet<String> t = new HashSet<>();
                t.add(fields[1]);
                nameSet.put(name, t);
            }
        }
        return nameSet;
    }

    /**
     * 获取bug 包含的描述类名称集合
     *
     * @return
     * @throws IOException
     */
    public static HashMap<Integer, String> getBugNameSet() throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(Utility.outputFileDir + "DescriptionClassName.txt"));
        HashMap<Integer, String> bugNameSet = new HashMap<>();
        String line;
        while ((line = reader.readLine()) != null) {
            String[] fields = line.split("\t");
            if (fields.length < 2) continue;
            else bugNameSet.put(Integer.parseInt(fields[0]), fields[1]);
        }
        return bugNameSet;
    }

    /**
     * 获取相关分数
     *
     * @param bugid
     * @return
     * @throws IOException
     */
    public static float[] getRelativeScore(Integer bugid) throws IOException {
        float[] relativeScore = new float[Utility.originFileCount];  //原始文件相关数组
        for (int i = 0; i < relativeScore.length; i++)
            relativeScore[i] = 0;

        String s = bugNameTable.get(bugid);  //该bug中包含的描述性类名
        if (s == null) //没有相关类名，返回0
            return relativeScore;

        String[] f = s.split(" ");  //获取类名数组
        Set<String> nameSet = new HashSet<>();
        for (int i = 0; i < f.length; i++)
            if (shortNameSet.containsKey(f[i]))
                nameSet.addAll(shortNameSet.get(f[i]));  //

        //  add the imported class
        HashMap<String, String> importTable = Utility.getImportTable(ImportFileName);
        Set<String> fullNameSet = idTable.keySet();  //类名集合
        Iterator<String> itr = nameSet.iterator();
        Set<String> appendage = new HashSet<>();
        while (itr.hasNext()) {
            String n = itr.next();
            String ims = importTable.get(n);
            if (ims == null) {
                continue;
            }
            String[] imclasses = ims.split(" ");
            for (int i = 0; i < imclasses.length; i++) {
                Iterator<String> all_itr = fullNameSet.iterator();
                while (all_itr.hasNext()) {
                    String tmpn = all_itr.next();
                    String backupname = tmpn;
                    if (Utility.project.compareTo("AspectJ") == 0) {
                        String[] namefields = tmpn.split("/");
                        int j;
                        for (j = 0; j < namefields.length; j++) {
                            if (namefields[j].compareTo("org") == 0) {
                                break;
                            }
                        }
                        tmpn = "org";
                        for (j = j + 1; j < namefields.length; j++) {
                            tmpn = tmpn + "." + namefields[j];
                        }
                    }
                    // end for aspectj
                    if (tmpn.contains(imclasses[i])) {
                        Integer l1 = tmpn.split("\\.").length;
                        Integer l2 = imclasses[i].split("\\.").length;
                        if (l1 - l2 <= 2) {
                            appendage.add(backupname);
                        }
                    }
                }
            }
        }
        //Calculate scores
        itr = appendage.iterator();
        while (itr.hasNext()) {
            Integer id = idTable.get(itr.next());
            relativeScore[id] = 0.2f;
        }

        itr = nameSet.iterator();
        while (itr.hasNext()) {
            Integer id = idTable.get(itr.next());
            relativeScore[id] = 0.5f;
        }
        return relativeScore;
    }


    public static void evaluate() throws IOException {
        init(); //初始化
        BufferedReader VSMReader = new BufferedReader(new FileReader(Utility.outputFileDir + VSMScoreFileName));
        BufferedReader GraphReader = new BufferedReader(new FileReader(Utility.outputFileDir + SimiScoreFileName));

        int count = 0;
        FileWriter writer = new FileWriter(Utility.outputFilePath);  //输出结果文件

        while (count++ < Utility.bugReportCount) {
            String vsmLine = VSMReader.readLine();
            Integer vsmId = Integer.parseInt(vsmLine.substring(0, vsmLine.indexOf(";")));       //bug ID
            float[] vsmVector = Utility.getVector(vsmLine.substring(vsmLine.indexOf(";") + 1)); //bug VSM向量

            tmp_group = null; //Map<String, Integer>

            // 对VSM进行修正 rVSM
            for (String key : lenTable.keySet()) {
                Double score = lenTable.get(key);  //长度分数
                Integer i = 0;
                while (true) {
                    String name = key + "@" + i.toString() + ".java"; //分段名
                    Integer id = methodIdTable.get(name);             //分段名索引
                    if (id == null) break;
                    vsmVector[id] = vsmVector[id] * score.floatValue();  //修正VSM Vector TF-IDF * LEN
                    i++;
                }
            }
            vsmVector = Utility.normalize(vsmVector);  //归一化


            String simiLine = GraphReader.readLine();  //相似bug信息
            Integer graphId = Integer.parseInt(simiLine.substring(0, simiLine.indexOf(";")));  //相似bug ID
            float[] simiVector = Utility.getVector(simiLine.substring(simiLine.indexOf(";") + 1)); //相似bug 相似度
            simiVector = Utility.normalize(simiVector);  //对相似向量进行归一化

            float[] finalR = combine(vsmVector, simiVector, alpha);  //最终的向量
            float[] finalScore = new float[Utility.originFileCount];  //最终分数向量

            int[] usedcount = new int[Utility.originFileCount];

            HashMap<Integer, ArrayList<Float>> scores = new HashMap<>();
            for (int counter = 0; counter < finalR.length; counter++) {
                String name = methodNameTable.get(counter);
                name = name.substring(0, name.indexOf('@'));
                Integer id = idTable.get(name);
                if (id == null) {
                    System.err.println(name);
                    Console console = System.console();
                    String delay_input = console.readLine();
                    continue;
                }
                /* automatically determine num of file to represent the origin file */
                if (scores.containsKey(id)) {
                    ArrayList<Float> t = scores.get(id);
                    t.add(finalR[counter]);
                } else {
                    ArrayList<Float> t = new ArrayList<>();
                    t.add(finalR[counter]);
                    scores.put(id, t);
                }
            }
            for (int i = 0; i < Utility.originFileCount; i++) {
                ArrayList<Float> t = scores.get(i);
                try {
                    Collections.sort(t, Collections.reverseOrder());
                } catch (Exception e) {
                    System.out.println(i);
                    continue;
                }
                finalScore[i] = t.get(0);
            }

            float[] groupScore = getRelativeScore(vsmId);  //获取相对分数
            for (int i = 0; i < Utility.originFileCount; i++) finalScore[i] = finalScore[i] + groupScore[i];
            Rank[] sort = sort(finalScore);  //排序后的分数

            Iterator<String> fileIt = fixTable.get(vsmId).iterator();    //该bug的修复文件
            Hashtable<Integer, String> fileIdTable = new Hashtable<>();  //
            while (fileIt.hasNext()) {
                String fileName = fileIt.next();         //某个修复文件
                Integer fileId = idTable.get(fileName);  //该修复文件的索引
                //未找到该文件索引或者没有改文件名
                if (fileId == null || fileName == null) {
                    System.out.println("null pointer");
                    System.out.println(fileName);
                    continue;
                }
                fileIdTable.put(fileId, fileName);
            }
            //
            Integer tmploc = 0;
            for (int i = 0; i < sort.length; i++) {
                Rank rank = sort[i];
                if ((!fileIdTable.isEmpty()) && fileIdTable.containsKey(rank.id)) {
                    String filename = nameTable.get(rank.id);
                    tmploc += LOCTable.get(filename);
                    Float percent = (float) tmploc / (float) TotalLOC;
                    writer.write(vsmId + "\t" + fileIdTable.get(rank.id) + "\t" + i + "\t" + rank.rank + " " + Utility.lineSeparator);
                    writer.flush();
                    break;
                } else {
                    String filename = nameTable.get(rank.id);
                    tmploc += LOCTable.get(filename);
                }
            }
        }
        writer.close();
    }


    /**
     * 获取Top k 准确率
     *
     * @param k
     */
    public static void getTopK(int k) throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(Utility.outputFilePath));
        String line;
        int sum = 0;
        Set<Integer> hasCountBugs = new HashSet<>();
        while ((line = reader.readLine()) != null) {
            String[] value = line.split("\t");
            if(hasCountBugs.contains(Integer.parseInt(value[0])))
                continue;
            hasCountBugs.add(Integer.parseInt(value[0]));
            if (Integer.parseInt(value[2]) < k)
                sum++;
        }
        System.out.println("Top" + k + ": " + (float)sum / hasCountBugs.size());
    }

    public static void getMRR() throws IOException{
        BufferedReader reader = new BufferedReader(new FileReader(Utility.outputFilePath));
        String line;
        float sum = 0;
        Set<Integer> hasCountBugs = new HashSet<>();
        while ((line = reader.readLine()) != null) {
            String[] value = line.split("\t");
            if(hasCountBugs.contains(Integer.parseInt(value[0])))
                continue;
            hasCountBugs.add(Integer.parseInt(value[0]));
            sum += 1/(Integer.parseInt(value[2]) + 1);
        }
        sum/= hasCountBugs.size();
        System.out.println("MRR: " + sum);
    }

    public static void getMAP() throws IOException{

    }
}
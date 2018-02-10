package evaluationSection4;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;

import analysisNewIO.MSRPIO;
import analysisNewIO.MrsPIO;
import analysisNewIO.PWLPIO;
import analysisNewIO.RuntimeCostAnalysis;
import entity.Resource;
import entity.SporadicTask;
import generatorTools.AllocationGeneator;
import generatorTools.SystemGenerator;
import utils.AnalysisUtils.CS_LENGTH_RANGE;
import utils.AnalysisUtils.RESOURCES_RANGE;
import utils.ResultReader;

public class PriorityOrderingTest {
	public static int MAX_PERIOD = 1000;
	public static int MIN_PERIOD = 1;
	public static int TOTAL_NUMBER_OF_SYSTEMS = 10000;
	public static int TOTAL_PARTITIONS = 16;

	static int NUMBER_OF_MAX_ACCESS_TO_ONE_RESOURCE = 2;
	static int NUMBER_OF_TASKS_ON_EACH_PARTITION = 3;
	static double RESOURCE_SHARING_FACTOR = 0.4;

	public static void main(String[] args) throws Exception {
		PriorityOrderingTest test = new PriorityOrderingTest();

		MSRPIO msrp = new MSRPIO();
		final CountDownLatch msrpwork = new CountDownLatch(6);
		for (int i = 1; i < 7; i++) {
			final int cslen = i;
			new Thread(new Runnable() {
				@Override
				public void run() {
					test.experimentIncreasingCriticalSectionLength(msrp, cslen, "MSRP");
					msrpwork.countDown();
				}
			}).start();
		}
		msrpwork.await();

		PWLPIO pwlp = new PWLPIO();
		final CountDownLatch pwlpwork = new CountDownLatch(6);
		for (int i = 1; i < 7; i++) {
			final int cslen = i;
			new Thread(new Runnable() {
				@Override
				public void run() {
					test.experimentIncreasingCriticalSectionLength(pwlp, cslen, "PWLP");
					pwlpwork.countDown();
				}
			}).start();
		}
		pwlpwork.await();

		MrsPIO mrsp = new MrsPIO();
		final CountDownLatch mrspwork = new CountDownLatch(6);
		for (int i = 1; i < 7; i++) {
			final int cslen = i;
			new Thread(new Runnable() {
				@Override
				public void run() {
					test.experimentIncreasingCriticalSectionLength(mrsp, cslen, "MrsP");
					mrspwork.countDown();
				}
			}).start();
		}
		mrspwork.await();

		ResultReader.priorityReader();

	}

	public void experimentIncreasingCriticalSectionLength(RuntimeCostAnalysis analysis, int cs_len, String name) {
		final CS_LENGTH_RANGE cs_range;
		switch (cs_len) {
		case 1:
			cs_range = CS_LENGTH_RANGE.VERY_SHORT_CS_LEN;
			break;
		case 2:
			cs_range = CS_LENGTH_RANGE.SHORT_CS_LEN;
			break;
		case 3:
			cs_range = CS_LENGTH_RANGE.MEDIUM_CS_LEN;
			break;
		case 4:
			cs_range = CS_LENGTH_RANGE.LONG_CSLEN;
			break;
		case 5:
			cs_range = CS_LENGTH_RANGE.VERY_LONG_CSLEN;
			break;
		case 6:
			cs_range = CS_LENGTH_RANGE.Random;
			break;
		default:
			cs_range = null;
			break;
		}

		SystemGenerator generator = new SystemGenerator(MIN_PERIOD, MAX_PERIOD, true, TOTAL_PARTITIONS, TOTAL_PARTITIONS * NUMBER_OF_TASKS_ON_EACH_PARTITION,
				RESOURCE_SHARING_FACTOR, cs_range, RESOURCES_RANGE.PARTITIONS, NUMBER_OF_MAX_ACCESS_TO_ONE_RESOURCE, false);

		long[][] Ris;
		String result = "";
		int RiDM = 0;
		int OPA = 0;
		int slackOPA = 0;

		int DMcannotOPAcan = 0;
		int DMcanOPAcannot = 0;
		int OPAcannotSBPOcan = 0;
		int OPAcanSBPOcannot = 0;
		int DMcannotSBPOAcan = 0;
		int DMcanSBPOcannot = 0;

		for (int i = 0; i < TOTAL_NUMBER_OF_SYSTEMS; i++) {
			ArrayList<SporadicTask> tasksToAlloc = generator.generateTasks();
			ArrayList<Resource> resources = generator.generateResources();
			generator.generateResourceUsage(tasksToAlloc, resources);
			ArrayList<ArrayList<SporadicTask>> tasks = new AllocationGeneator().allocateTasks(tasksToAlloc, resources, generator.total_partitions, 0);

			boolean DMok = false, OPAok = false, SBPOok = false;
			Ris = analysis.getResponseTimeDM(tasks, resources, true, true, false);
			if (isSystemSchedulable(tasks, Ris)) {
				RiDM++;
				DMok = true;
			}

			Ris = analysis.getResponseTimeBySBPO(tasks, resources, false);
			if (isSystemSchedulable(tasks, Ris)) {
				slackOPA++;
				SBPOok = true;
			}

			Ris = analysis.getResponseTimeByOPA(tasks, resources, false);
			if (isSystemSchedulable(tasks, Ris)) {
				OPA++;
				OPAok = true;
			}

			if (!DMok && OPAok)
				DMcannotOPAcan++;

			if (DMok && !OPAok)
				DMcanOPAcannot++;

			if (OPAok && !SBPOok) {
				OPAcanSBPOcannot++;
			}

			if (!OPAok && SBPOok)
				OPAcannotSBPOcan++;

			if (!DMok && SBPOok)
				DMcannotSBPOAcan++;

			if (DMok && !SBPOok)
				DMcanSBPOcannot++;

			System.out.println(name + " " + 2 + " " + 4 + " " + cs_len + " times: " + i);

		}

		result = name + "   DM: " + (double) RiDM / (double) TOTAL_NUMBER_OF_SYSTEMS + "    OPA: " + (double) OPA / (double) TOTAL_NUMBER_OF_SYSTEMS
				+ "    SBPO: " + (double) slackOPA / (double) TOTAL_NUMBER_OF_SYSTEMS + "    OPA ok & DM fail: " + DMcannotOPAcan + "    OPA fail & DM ok: "
				+ DMcanOPAcannot + "    OPA ok & SBPO fail: " + OPAcanSBPOcannot + "    OPA fail & SBPO ok: " + OPAcannotSBPOcan + "   SBPO ok & DM fail: "
				+ DMcannotSBPOAcan + "   SBPO fail & DM ok: " + DMcanSBPOcannot + "\n";

		writeSystem((name + " " + 2 + " " + 4 + " " + cs_len), result);
	}

	public boolean isSystemSchedulable(ArrayList<ArrayList<SporadicTask>> tasks, long[][] Ris) {
		for (int i = 0; i < tasks.size(); i++) {
			for (int j = 0; j < tasks.get(i).size(); j++) {
				if (tasks.get(i).get(j).deadline < Ris[i][j])
					return false;
			}
		}
		return true;
	}

	public void writeSystem(String filename, String result) {
		PrintWriter writer = null;
		try {
			writer = new PrintWriter(new FileWriter(new File("result/" + filename + ".txt"), false));
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

		writer.println(result);
		writer.close();
	}
}

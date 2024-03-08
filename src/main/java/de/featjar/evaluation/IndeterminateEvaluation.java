package de.featjar.evaluation;

import de.featjar.base.FeatJAR;
import de.featjar.base.computation.Computations;
import de.featjar.base.computation.IComputation;
import de.featjar.base.data.Pair;
import de.featjar.base.io.csv.CSVFile;
import de.featjar.evaluation.process.Result;
import de.featjar.evaluation.util.ModelReader;
import de.featjar.formula.analysis.VariableMap;
import de.featjar.formula.analysis.bool.BooleanAssignment;
import de.featjar.formula.analysis.bool.BooleanClauseList;
import de.featjar.formula.analysis.bool.ComputeBooleanRepresentation;
import de.featjar.formula.analysis.bool.IBooleanRepresentation;
import de.featjar.formula.analysis.mig.solver.MIGBuilder;
import de.featjar.formula.analysis.mig.solver.ModalImplicationGraph;
import de.featjar.formula.analysis.sat4j.ASAT4JAnalysis;
import de.featjar.formula.analysis.sat4j.ComputeCoreSAT4J;
import de.featjar.formula.analysis.sat4j.indeterminate.*;
import de.featjar.formula.io.HiddenFormulaFormats;
import de.featjar.formula.structure.formula.IFormula;
import de.featjar.formula.structure.formula.connective.And;
import de.featjar.formula.structure.formula.connective.BiImplies;
import de.featjar.formula.transformer.ComputeCNFFormula;
import de.featjar.formula.transformer.ComputeNNFFormula;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class IndeterminateEvaluation extends Evaluator {
    private long timeoutV ;
    private  HashMap< String, Pair< IFormula, Pair<List<String>,List< BiImplies >>>> models = new HashMap<>();

    @Override
    public void init() throws Exception {
        super.init();
        timeoutV = optionParser.getResult(timeout).get();
        for(String modelName : systemNames) {
            ModelReader<Pair<IFormula, Pair<List<String>, List<BiImplies>>>> modelReader = new ModelReader<>(modelPath.resolve(modelName), HiddenFormulaFormats.getInstance());
            models.put(modelName, modelReader.loadFile(modelReader.getPathToFiles()).get());
        }
    }



    @Override
    protected void runEvaluation() throws Exception {
        for(String modelName: models.keySet() ) {
            FeatJAR.log().info("Running evaluation for "+modelName);
            Pair< IFormula, Pair<List<String>,List< BiImplies >>> model = models.get(modelName);
            CSVFile csvFile = this.addCSVWriter(modelName, "id", "IA", "IAS", "pre1-IA","pre1DCBe-IA","pre1DCDu-IA", "pre2-IA", "pre3-IA", "pre4-IA", "pre5-IA", "impB-IA", "impA-IA", "update-pre1-IA","CorrectRes");
            IComputation<IFormula> formula = Computations.of(model.getKey());
            ComputeBooleanRepresentation<IFormula, IBooleanRepresentation> cnf =
                    formula.map(ComputeNNFFormula::new)
                            .map(ComputeCNFFormula::new)
                            .map(ComputeBooleanRepresentation::new);
            IComputation<BooleanClauseList> clauses = cnf.map(Computations::getKey).cast(BooleanClauseList.class);
            VariableMap variableMap = cnf.map(Computations::getValue).compute();
            BooleanAssignment hiddenVariables = new BooleanAssignment(model.getValue().getKey().stream().mapToInt(x -> variableMap.get(x).get()).toArray());
            MIGBuilder migBuilder=  clauses
                    .map(MIGBuilder::new);
            ModalImplicationGraph modalImplicationGraph = migBuilder.compute();
            List<Integer> indexes = optionParser.getResult(algorithmIterationsOption).get();
            BooleanAssignment deadCore = new ComputeCoreSAT4J(clauses).compute();
            int formulaSize = formula.getChildrenCount();
            int hiddenVariablesSize  = hiddenVariables.size();
            for (int i : indexes) {
                // initialize data
                assert formulaSize == formula.getChildrenCount();
                assert hiddenVariablesSize == hiddenVariables.size();
                // initialise complete indeterminate analysis
                ComputeIndeterminate normalIndeterminate = new ComputeIndeterminate(clauses);
                ComputeIndeterminateSlicing normalIndeterminateSlicing = new ComputeIndeterminateSlicing(clauses);
                //initalize core Dead  Analyse
                ComputeCoreSAT4J computeCoreSAT4J = new ComputeCoreSAT4J(clauses);
                // initialise pre process
                PreprocessIff preprocessIff = new PreprocessIff(formula);
                PreprocessIff preprocessIff1 = new PreprocessIff(formula);
                preprocessIff1.set(PreprocessIff.CORE_DEAD_FEATURE, deadCore);
                PreprocessIff preprocessIff2 = new PreprocessIff(formula);
                PreprocessIffV2 preprocessIffV2 = new PreprocessIffV2(formula);
                List<IFormula> andChilds = (List<IFormula>) formula.compute().getChildren();
                PreprocessIffSort preprocessIffSort = new PreprocessIffSort(Computations.of(new And( andChilds)));
                PreprocessIffComp preprocessIffComp = new PreprocessIffComp(formula);
                PreprocessIffCompSort preprocessIffCompSort = new PreprocessIffCompSort(formula);
                PreprocessImGraph preprocessImGraph = new PreprocessImGraph(Computations.of(modalImplicationGraph));
                ComputeBiImplicationFormula biImplicationFormula = new ComputeBiImplicationFormula(formula
                        .map(ComputeNNFFormula::new)
                        .map(ComputeCNFFormula::new)
                        .compute(), variableMap);
                List<Result<BooleanAssignment>> result = new ArrayList<>();
                result.add(compute(new Analysis(normalIndeterminate, hiddenVariables)));
                result.add(compute(new Analysis(normalIndeterminateSlicing, hiddenVariables)));
                result.add(compute(new Analysis(preprocessIff, variableMap, normalIndeterminate, hiddenVariables)));
                result.add(compute(new Analysis(preprocessIff1, variableMap, normalIndeterminate, hiddenVariables)));
                result.add(compute(new Analysis(computeCoreSAT4J,preprocessIff2, variableMap, normalIndeterminate, hiddenVariables)));
                result.add(compute(new Analysis(preprocessIffV2, variableMap, normalIndeterminate, hiddenVariables)));
                result.add(compute(new Analysis(preprocessIffSort, variableMap, normalIndeterminate, hiddenVariables)));
                result.add(compute(new Analysis(preprocessIffComp, variableMap, normalIndeterminate, hiddenVariables)));
                result.add(compute(new Analysis(preprocessIffCompSort, variableMap, normalIndeterminate, hiddenVariables)));
                result.add(compute(new Analysis(preprocessImGraph, variableMap, normalIndeterminate, hiddenVariables)));
                result.add(compute(new Analysis(migBuilder, normalIndeterminate, hiddenVariables)));
                result.add(compute(new Analysis(biImplicationFormula, variableMap, preprocessIffSort, normalIndeterminate, hiddenVariables)));
                int correct = result.size();
                List<Integer> res =  result.get(0).getResult().streamValues().map(Pair::getKey).sorted().collect(Collectors.toList());
                for(int j = 1; j < result.size();j++ ) {
                    if(result.get(j).getResult() != null) {
                        List<Integer> otherRes = result.get(j).getResult().streamValues().map(Pair::getKey).sorted().collect(Collectors.toList());
                        if (!otherRes.equals(res)) {
                            List<Integer> wrong = otherRes.stream().filter(x -> !res.contains(x)).collect(Collectors.toList());
                            FeatJAR.log().info(j + ": " + otherRes.size() + " " + res.size());
                            wrong.addAll(res.stream().filter(x -> !otherRes.contains(x)).collect(Collectors.toList()));
                            FeatJAR.log().info(j + ": " + wrong);
                            List<String> wrongName = wrong.stream().map(x -> variableMap.get(x).get()).collect(Collectors.toList());
                            FeatJAR.log().info(j + ": " + wrongName);
                            correct--;
                        }
                    }
                }
                List<String> outputLine = result.stream().map(e -> {
                    if(e.getTime() == Long.MAX_VALUE) return "Timeout";
                    return TimeUnit.MILLISECONDS.convert(e.getTime(), TimeUnit.NANOSECONDS) + "";
                }
                ).collect(Collectors.toList());
                outputLine.add(0,i+"");
                outputLine.add(correct+"");

                csvFile.addLine(outputLine);
              //  FeatJAR.log().message("");
            }
            csvFile.flush();
            FeatJAR.log().info("Finished evaluation for "+modelName);
        }

    }



    private Result<BooleanAssignment> compute(Analysis analysis){
        Result<BooleanAssignment> result = new Result<>();
        long start, end = 0;
        start = System.nanoTime();
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        Future<BooleanAssignment> res =  executorService.submit(analysis);
        try {
            BooleanAssignment assignment = res.get(timeoutV,TimeUnit.MINUTES);
            end = System.nanoTime();
            result.setResult(assignment);
            result.setTime(end-start);
      //      FeatJAR.log().message("Finished:"+analysis.info());
        } catch ( TimeoutException e) {
            res.cancel(true);
            result.setTime(Long.MAX_VALUE);
            result.setResult(null);
    //        FeatJAR.log().message("Timeout: "+analysis.info());
        }catch (InterruptedException| ExecutionException e ){
            e.printStackTrace();
        } finally{
            executorService.shutdownNow();
        }

        return result;
    }

    private class Analysis implements Callable<BooleanAssignment>{
        ASAT4JAnalysis.Solution<BooleanAssignment> indeterminate;
        ComputeIndeterminate indeterminateAnalyse;
        IndeterminatePreprocessFormula preprocessF;
        IndeterminatePreprocess preprocess;
        PreprocessImGraph preprocessImGraph;
        ComputeBiImplicationFormula formula;
        int type = 0;
        VariableMap map;
        BooleanAssignment hiddenVariables;

        ComputeCoreSAT4J coreDeadAnalysis;

        MIGBuilder migBuilder;

        public Analysis(ASAT4JAnalysis.Solution<BooleanAssignment> indeterminate, BooleanAssignment hiddenVariables){
            this.indeterminate = indeterminate;
            this.hiddenVariables = hiddenVariables;
            type = 0;
        }
        public Analysis(IndeterminatePreprocess preprocess, VariableMap variableMap, ComputeIndeterminate indeterminate, BooleanAssignment hiddenVariables){
            this.indeterminateAnalyse = indeterminate;
            this.hiddenVariables = hiddenVariables;
            this.preprocess = preprocess;
            map = variableMap;
            type = 1;
        }
        public Analysis(ComputeBiImplicationFormula formula, VariableMap variableMap,  IndeterminatePreprocessFormula preprocess, ComputeIndeterminate indeterminate, BooleanAssignment hiddenVariables){
            this.indeterminateAnalyse = indeterminate;
            this.hiddenVariables = hiddenVariables;
            this.preprocessF = preprocess;
            map = variableMap;
            this.formula = formula;
            type = 2;
        }
        public Analysis(ComputeCoreSAT4J computeCoreSAT4J, IndeterminatePreprocess preprocess, VariableMap variableMap, ComputeIndeterminate indeterminate, BooleanAssignment hiddenVariables){
            this.indeterminateAnalyse = indeterminate;
            this.hiddenVariables = hiddenVariables;
            this.preprocess = preprocess;
            coreDeadAnalysis = computeCoreSAT4J;
            map = variableMap;
            type = 3;
        }
        public Analysis(MIGBuilder migBuilder, ComputeIndeterminate indeterminate, BooleanAssignment hiddenVariables){
            this.indeterminateAnalyse = indeterminate;
            this.hiddenVariables = hiddenVariables;
            this.migBuilder = migBuilder;
            type = 4;
        }

        @Override
        public BooleanAssignment call() throws Exception {
            if(type == 0){
                return indeterminate
                        .set(ComputeIndeterminate.VARIABLES_OF_INTEREST, hiddenVariables)
                        .compute();
            }else if(type == 1){
                return indeterminateAnalyse
                        .set(ComputeIndeterminate.VARIABLES_OF_INTEREST,preprocess
                                .set(IndeterminatePreprocess.VARIABLE_MAP,map)
                                .set(IndeterminatePreprocess.VARIABLES_OF_INTEREST,hiddenVariables)
                                .compute())
                        .compute();
            }else if (type == 2) {
                return indeterminateAnalyse
                        .set(ComputeIndeterminate.VARIABLES_OF_INTEREST, preprocessF.
                                addBiImplies(formula.compute())
                                .set(IndeterminatePreprocess.VARIABLE_MAP, map)
                                .set(IndeterminatePreprocess.VARIABLES_OF_INTEREST, hiddenVariables)
                                .compute())
                        .compute();
            }else if(type == 3){
                return indeterminateAnalyse
                        .set(ComputeIndeterminate.VARIABLES_OF_INTEREST,preprocess
                                .set(IndeterminatePreprocess.VARIABLE_MAP,map)
                                .set(IndeterminatePreprocess.VARIABLES_OF_INTEREST,hiddenVariables)
                                .set(IndeterminatePreprocess.CORE_DEAD_FEATURE,coreDeadAnalysis.compute())
                                .compute())
                        .compute();
            }else {
                preprocessImGraph = new PreprocessImGraph(Computations.of(migBuilder.compute()));
                return indeterminateAnalyse
                        .set(ComputeIndeterminate.VARIABLES_OF_INTEREST,preprocessImGraph
                                .set(IndeterminatePreprocess.VARIABLES_OF_INTEREST,hiddenVariables)
                                .compute())
                        .compute();
            }
        }

        private String info(){
            switch (type){
                case 0:
                    return " "+indeterminate;
                case 1:
                    return " "+preprocess+" => "+indeterminateAnalyse;
                case 2:
                    return " "+ formula+" => "+ preprocessF +" =>"+indeterminateAnalyse;
                case 3:
                    return " "+coreDeadAnalysis+" => " +preprocess+ " => "+ indeterminate;
                default:
                    return " "+migBuilder+" => "+preprocessImGraph+" => "+indeterminateAnalyse;
            }
        }
    }

}

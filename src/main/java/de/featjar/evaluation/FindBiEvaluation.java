package de.featjar.evaluation;

import de.featjar.base.FeatJAR;
import de.featjar.base.cli.Option;
import de.featjar.base.computation.Computations;
import de.featjar.base.computation.IComputation;
import de.featjar.base.data.Pair;
import de.featjar.base.io.csv.CSVFile;
import de.featjar.evaluation.process.Result;
import de.featjar.evaluation.util.ModelReader;
import de.featjar.formula.analysis.VariableMap;
import de.featjar.formula.analysis.bool.*;
import de.featjar.formula.analysis.sat4j.indeterminate.*;
import de.featjar.formula.io.FormulaFormats;
import de.featjar.formula.io.HiddenFormulaFormats;
import de.featjar.formula.structure.formula.IFormula;
import de.featjar.formula.structure.formula.connective.And;
import de.featjar.formula.structure.formula.connective.BiImplies;
import de.featjar.formula.structure.formula.connective.Or;
import de.featjar.formula.structure.formula.predicate.Literal;
import de.featjar.formula.transformer.ComputeCNFFormula;
import de.featjar.formula.transformer.ComputeNNFFormula;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class FindBiEvaluation extends Evaluator {
    private long timeoutV ;
     private Pair< IFormula, Pair<List<String>,List< BiImplies >>> infoModel;

     private String modelName;

    private String date;

    private Option<String> TIME_OPTION = new Option<>("date", Option.StringParser,"");

    @Override
    public void init() throws Exception {
        super.init();
        timeoutV = optionParser.getResult(timeout).get();
        Path file = optionParser.getResult(INPUT_OPTION).get();
        ModelReader<Pair<IFormula, Pair<List<String>, List<BiImplies>>>> modelReader = new ModelReader<>(file, HiddenFormulaFormats.getInstance());
        infoModel = modelReader.loadFile(modelReader.getPathToFiles()).get();
        modelName = file.getFileName().toString();
        date = optionParser.getResult(TIME_OPTION).get();


    }

    @Override
    protected void initSubPaths() {
        super.initSubPaths();
        csvPath = outputPath.resolve("data").resolve("data-"+date);
    }

    @Override
    public List<Option<?>> getOptions() {
        return List.of(
                INPUT_OPTION,
                OUTPUT_OPTION,
                modelsPathOption,
                resourcesPathOption,
                timeout,
                randomSeed,
                systemsOption,
                systemIterationsOption,
                algorithmIterationsOption,
                TIME_OPTION);
    }


    @Override
    protected void runEvaluation() throws Exception {
            FeatJAR.log().info("Running evaluation for "+modelName);
            CSVFile csvFile = this.addCSVWriter(modelName, "id", "update-pre3-IA");
             IComputation<IFormula> formula = Computations.of(infoModel.getKey());
            ComputeBooleanRepresentation<IFormula, IBooleanRepresentation> cnf =
                formula.map(ComputeNNFFormula::new)
                        .map(ComputeCNFFormula::new)
                        .map(ComputeBooleanRepresentation::new);
            IComputation<BooleanClauseList> clauses = cnf.map(Computations::getKey).cast(BooleanClauseList.class);
            VariableMap variableMap = cnf.map(Computations::getValue).compute();

            List<BooleanClause> temp = clauses.compute().stream().collect(Collectors.toSet()).stream().map(BooleanClause::new).collect(Collectors.toList());
            BooleanClauseList clauses2 = new BooleanClauseList(temp, clauses.compute().getVariableCount());
            BooleanAssignment hiddenVariables = new BooleanAssignment(infoModel.getValue().getKey().stream().mapToInt(x -> variableMap.get(x).get()).toArray());
            IFormula formula_simple = new And( clauses2.stream().map(x -> new Or(x.stream().mapToObj(y -> new Literal(y>0,variableMap.get(Math.abs(y)).get())).collect(Collectors.toList()))).collect(Collectors.toList()));
            List<Integer> indexes = optionParser.getResult(algorithmIterationsOption).get();
            for (int i : indexes) {
                // initialize data
                ComputeBiImplicationFormula biImplicationFormula = new ComputeBiImplicationFormula(formula_simple, variableMap);
                List<Result<BooleanAssignment>> result = new ArrayList<>();
                result.add(compute(variableMap,clauses.compute(),hiddenVariables,csvFile,formula,biImplicationFormula));
                List<String> outputLine = result.stream().map(e -> {
                            return TimeUnit.MILLISECONDS.convert(e.getTime(), TimeUnit.NANOSECONDS) + "";
                        }
                ).collect(Collectors.toList());
                outputLine.add(0,i+"");

                csvFile.addLine(outputLine);
            }
            csvFile.flush();
            FeatJAR.log().info("Finished evaluation for "+modelName);


    }



    private Result<BooleanAssignment> compute(VariableMap map, BooleanClauseList clauses_h, BooleanAssignment hiddenVariables,CSVFile csvFile,IComputation<IFormula> formula,ComputeBiImplicationFormula findBi){
        Result<BooleanAssignment> result = new Result<>();

        List<IFormula> andChilds = (List<IFormula>) formula.compute().getChildren();
        long start, end;
        start = System.nanoTime();
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        Future<BooleanAssignment> res =  executorService.submit(()->{
            List<BooleanClause> temp = clauses_h.stream().collect(Collectors.toSet()).stream().map(BooleanClause::new).collect(Collectors.toList());
            IComputation<BooleanClauseList> clauses = Computations.of(new BooleanClauseList(temp,clauses_h.getVariableCount()));
           return new ComputeIndeterminate(clauses)
                   .set(ComputeIndeterminateSlicing.VARIABLES_OF_INTEREST,new PreprocessIffV2(Computations.of(new And(andChilds)))
                           .addBiImplies(findBi
                                   .set(ComputeBiImplicationFormula.MAXIMUM_CLAUSE_SIZE,30)
                                   .compute())
                           .set(PreprocessIffV2.VARIABLES_OF_INTEREST,hiddenVariables)
                           .set(PreprocessIffV2.VARIABLE_MAP,map)
                           .compute())
                   .compute();
        });
        try {
            BooleanAssignment assignment;

            assignment = res.get(timeoutV,TimeUnit.MINUTES);
            end = System.nanoTime();
            result.setResult(assignment);
            result.setTime(end-start);
        }catch (InterruptedException| ExecutionException e ){
            e.printStackTrace();
        } catch (TimeoutException e) {
            res.cancel(true);
            result.setTime(Long.MAX_VALUE);
            result.setResult(null);
            List<String> output = new ArrayList<>();
            output.add(0,"");
            output.add(1,"Timeout");
            csvFile.addLine(output);
            csvFile.flush();
            System.exit(0);
        } finally{
            executorService.shutdownNow();
        }

        return result;
    }



}


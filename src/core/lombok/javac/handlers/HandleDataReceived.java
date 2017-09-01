package lombok.javac.handlers;

import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.model.JavacElements;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCAnnotation;
import com.sun.tools.javac.tree.JCTree.JCBlock;
import com.sun.tools.javac.tree.JCTree.JCCatch;
import com.sun.tools.javac.tree.JCTree.JCClassDecl;
import com.sun.tools.javac.tree.JCTree.JCEnhancedForLoop;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.JCTree.JCFieldAccess;
import com.sun.tools.javac.tree.JCTree.JCIf;
import com.sun.tools.javac.tree.JCTree.JCMethodDecl;
import com.sun.tools.javac.tree.JCTree.JCMethodInvocation;
import com.sun.tools.javac.tree.JCTree.JCModifiers;
import com.sun.tools.javac.tree.JCTree.JCNewClass;
import com.sun.tools.javac.tree.JCTree.JCStatement;
import com.sun.tools.javac.tree.JCTree.JCTypeParameter;
import com.sun.tools.javac.tree.JCTree.JCVariableDecl;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;
import com.sun.tools.javac.util.Name;
import lombok.DataReceived;
import lombok.core.AST;
import lombok.core.AnnotationValues;
import lombok.javac.Javac;
import lombok.javac.JavacAnnotationHandler;
import lombok.javac.JavacNode;
import lombok.javac.JavacTreeMaker;
import org.mangosdk.spi.ProviderFor;
import static lombok.javac.Javac.*;
import static lombok.javac.handlers.JavacHandlerUtil.*;

/**
 * Created by ttreibmann on 21.06.17.
 *
 * Handler for annotation @DataReeceived( value = "<custom commandID>"; controller = "<Name of controller class")
 * Inject method dataReceivied(ConnectionEvent evt){...} in annotated class
 * Only classes are a valid target for the annotation @DataReceived
 * creates
 *
 *   private static final org.apache.commons.logging.Log log = org.apache.commons.logging.LogFactory.getLog(Person.class);
 *   private java.util.List<java.util.concurrent.Future<java.lang.String>> future = new java.util.ArrayList<>();
 *   private java.util.concurrent.ExecutorService processingThreads = java.util.concurrent.Executors.newFixedThreadPool(5);
 *   private de.sourcepark.smd.base.queue.ISCPQueue queue;
 *
 *   private void init() {
 *       try {
 *           queue = de.sourcepark.smd.base.output.OutputQueueCollection.getInstance().get(Mavenproject1Controller.QUEUE_ID);
 *       } catch (final java.lang.Exception initEx) {
 *           log.error("Field initialization failed. Error occurred.", initEx);
 *       }
 *   }
 *
 *   @java.lang.Override
 *   public void dataReceived(final de.sourcepark.smd.ocl.ConnectionEvent evt) {
 *       this.init();
 *       de.sourcepark.smd.base.util.scp.SCPDataObject scpd = evt.getDataObject();
 *       try {
 *           if (scpd.hasStatus()) {
 *               this.doStatusProcessing();
 *               return;
 *           }
 *           if (scpd.isDirty()) {
 *               log.error("Unwanted modification detected. Message is flagged as dirty.");
 *               return;
 *           }
 *           if (scpd.getCommand("VALID") != null) {
 *               for (final java.util.concurrent.Future f : future) {
 *                   if (f.isDone()) {
 *                       this.doCleanupOrProcessing(f);
 *                   }
 *               }
 *               future.add(processingThreads.submit(new Mavenproject1Controller(scpd)));
 *           } else if (scpd.getCommand("ALIVE") != null) {
 *               de.sourcepark.smd.base.util.scp.SCPStatusObject scps = new de.sourcepark.smd.base.util.scp.SCPStatusObject(scpd);
 *               de.sourcepark.smd.base.util.scp.SCPCommand scpsAddParam = scps.getCommand("ALIVE");
 *               scpsAddParam.addParam(new de.sourcepark.smd.base.util.scp.SCPParameter("Version", "string", de.sourcepark.smd.base.config.SMDConfiguration.getVersionString(Person.class)));
 *               queue.offer(scps);
 *           } else if ((scpd.getCommand("STOP") != null) && scpd.getSource().equalsIgnoreCase(de.sourcepark.smd.base.config.SMDConfiguration.getInstance().getIdentity())) {
 *               this.stop();
 *           } else {
 *               log.error("Unprocessable message received. Possible conflict between command and context. Check message.");
 *           }
 *       } catch (final de.sourcepark.smd.base.config.SMDConfigurationException ex) {
 *           log.error("SMDConfigurationException: Configuration error occurred.", ex);
 *       } catch (final org.apache.commons.configuration.ConfigurationException ex2) {
 *           log.error("ConfigurationException: Configuration error occurred.", ex2);
 *       } catch (final java.lang.Throwable th) {
 *           log.fatal("Unexpected Exception", th);
 *       }
 *   }
 *
 */
@ProviderFor(JavacAnnotationHandler.class)
public class HandleDataReceived extends JavacAnnotationHandler<DataReceived> {

    //context of compiler object
    private JavacElements elements;
    //custom command id provided by annotaionparameter
    private String command;
    //simpel class name of controller provided by annotationparameter
    private String controller;
    //simple class name of annotated class
    private String classname;
    //name of node of fieldvaraible processingThreads
    private Name processingThreadsName;
    //name of node of fieldvaraible queue
    private Name queueName;

    /**
     * init elements command and controller
     * validates command
     * generates needed fields and methods for dataReceived(ConnectionEvent evt){...}
     * @param annotation The actual annotation - use this object to retrieve the annotation parameters.
     * @param ast The javac AST node representing the annotation.
     * @param annotationNode The Lombok AST wrapper around the 'ast' parameter. You can use this object
     * to travel back up the chain (something javac AST can't do) to the parent of the annotation, as well
     */
    @Override
    public void handle(final AnnotationValues<DataReceived> annotation, final JCAnnotation ast, final JavacNode annotationNode) {
        //get context of compiler object
        elements = JavacElements.instance(annotationNode.getContext());
        //get value of annotationparameter value
        command = annotation.getInstance().value();
        //get value of annotationparameter controller
        controller = annotation.getInstance().controller();

        // validate command for SCPDataObject.getCommand(...)
        // at error if validation fails
        if (!(command.equals("STOP") || command.equals("ALIVE") || command.equals("VALID") || command.equals("INVALID"))) {
            annotationNode.addError("No valid command for @DataReceived('<Command>') specified. InvalidCommand: " + command);
            return;
        }
        // get enclosing node of original node
        final JavacNode typeNode = annotationNode.up();
        //generate needed fields for dataReceived(ConnectionEvent evt){..}
        generateFields(typeNode, annotationNode);
        //generated needed methods for dataReceived(ConnectionEvent evt){..} also generate dataReceived(ConnectionEvent evt){..}
        generateMethods(typeNode, annotationNode);
    }

    /*generates and injects Fields:
    *private ExecutorService processingThreads= Executors.newFixedThreadPool(5);
    *private java.util.List<java.util.concurrent.Future<java.lang.String>> future = new java.util.ArrayList<>();
    *private de.sourcepark.smd.base.queue.ISCPQueue queue will be initiated in init()
    */
    private void generateFields(final JavacNode typeNode, final JavacNode source) {
        //generates private java.util.List<java.util.concurrent.Future<java.lang.String>> future = new java.util.ArrayList<>();
        final JCVariableDecl futureStringList = createFutureStringList(typeNode, source.get());
        //generates private ExecutorService processingThreads= Executors.newFixedThreadPool(5);
        final JCVariableDecl processingThreadsES = createProcessingThreadsES(typeNode, source.get());
        //generates private de.sourcepark.smd.base.queue.ISCPQueue
        final JCVariableDecl queueISCP = createqueueISCP(typeNode, source.get());
        //mark fields as generated by lombok and inject them
        injectFieldAndMarkGenerated(typeNode, futureStringList);
        injectFieldAndMarkGenerated(typeNode, processingThreadsES);
        injectFieldAndMarkGenerated(typeNode, queueISCP);
    }
    //generates private de.sourcepark.smd.base.queue.ISCPQueue queue
    private JCVariableDecl createqueueISCP(final JavacNode typeNode, final JCTree source) {
        //get treemaker to be able to define new ast nodes
        final JavacTreeMaker maker = typeNode.getTreeMaker();
        //select type of queque
        final JCFieldAccess queueISCPType = maker.Select(maker.Ident(elements.getName("de.sourcepark.smd.base.queue")), elements.getName("ISCPQueue"));
        //set Flag of fieldvariable queue
        final JCModifiers mods = maker.Modifiers(Flags.PRIVATE);
        //name fieldvariable queue "queue"
        queueName = typeNode.toName("queue");
        //create definition of variable queue with specific parameters and validate outcome node
        //returns the definition of the variable queue as node
        return recursiveSetGeneratedBy(maker.VarDef(mods, queueName, queueISCPType, null), source, typeNode.getContext());
    }
    //generates AND initiates private ExecutorService processingThreads= Executors.newFixedThreadPool(5);
    private JCVariableDecl createProcessingThreadsES(final JavacNode typeNode, final JCTree source) {
        //get treemaker to be able to define new ast nodes
        final JavacTreeMaker maker = typeNode.getTreeMaker();
        //select type of processingThreads
        final JCFieldAccess processingThreadType = maker.Select(maker.Ident(elements.getName("java.util.concurrent")), elements.getName("ExecutorService"));
        //set Flag of fieldvariable processingThreads
        final JCModifiers mods = maker.Modifiers(Flags.PRIVATE);
        //name fieldvariable processingThreads "processingThreads"
        processingThreadsName = typeNode.toName("processingThreads");
        //invoke method java.util.concurrent.Executors.newFixedThreadPool(5) for initiation of variable processingThreads
        final JCMethodInvocation executors = maker.Apply(List.<JCExpression>nil(), chainDotsString(typeNode, "java.util.concurrent.Executors.newFixedThreadPool"), List.<JCExpression>of(maker.Literal(CTC_INT, 5)));
        //create definition of variable processingThreads with specific parameters and validate outcome node
        //returns the definition of the variable processingThreads as node
        return recursiveSetGeneratedBy(maker.VarDef(mods, processingThreadsName, processingThreadType, executors), source, typeNode.getContext());
    }
    //generates AND initiates private java.util.List<java.util.concurrent.Future<java.lang.String>> future = new java.util.ArrayList<>();
    private JCVariableDecl createFutureStringList(final JavacNode typeNode, final JCTree source) {
        //get treemaker to be able to define new ast nodes
        final JavacTreeMaker maker = typeNode.getTreeMaker();
        //select type of java.util.List
        final JCFieldAccess listType = maker.Select(maker.Ident(elements.getName("java.util")), elements.getName("List"));
        //select type of java.util.concurrent.Future
        final JCFieldAccess futureType = getFutureType(maker);
        //select type of java.util.ArrayList
        final JCFieldAccess arrayList = maker.Select(maker.Ident(elements.getName("java.util")), elements.getName("ArrayList"));
        //apply generic Type <> to selected ArrayList type
        final JCExpression arrayListTypeWithGeneric = maker.TypeApply(arrayList, List.<JCExpression>nil());
        //set flag of fieldvariable future
        final JCModifiers mods = maker.Modifiers(Flags.PRIVATE);
        //name fieldvariable future "future"
        final Name futureName = typeNode.toName("future");
        //select type of String
        final JCExpression stringType = genJavaLangTypeRef(typeNode, "String");
        //store type of string in a list of expressions
        final ListBuffer<JCExpression> typeArgsSTtoF = new ListBuffer<JCExpression>();
        typeArgsSTtoF.append(stringType);
        //apply expression of type of string to type of java.util.concurrent.Future as generic type
        final JCExpression futureTypeWithString = maker.TypeApply(futureType, typeArgsSTtoF.toList());
        //store type of java.util.concurrent.Future<String> in a list of expressions
        final ListBuffer<JCExpression> typeArgsFtoList = new ListBuffer<JCExpression>();
        typeArgsFtoList.append(futureTypeWithString);
        //apply expression of type of java.util.concurrent.Future<String> to java.util.List as generic type
        final JCTree.JCExpression paramType = maker.TypeApply(listType, typeArgsFtoList.toList());
        //create new instance of class ArrayList with selected typ java.util.ArrayList

        // Arg 1 is the enclosing class, but we won't instantiate an inner class.
        // Arg 2 is a list of type parameters (of the enclosing class).
        // Arg 3 is the actual class expression.
        // Arg 4 is a list of arguments to pass to the constructor.
        // Arg 5 is a class body, for creating an anonymous class.
        final JCNewClass newArrayList = maker.NewClass(null,
                List.<JCExpression>nil(), arrayListTypeWithGeneric,
                List.<JCExpression>nil(), null);
        //create definition of variable future with specific parameters and validate outcome node
        //returns the definition of the variable future as node
        return recursiveSetGeneratedBy(maker.VarDef(mods, futureName, paramType, newArrayList), source, typeNode.getContext());
    }

    /*ensures that annotation is on class level
    *saves simplename of annotated class in variable classname
    *searches for field log created by @CommonsLog - with @DataReceived annotated class must be annotated with @Commonslog
    * and stores field in JavacNode logField - Throws error if not found
    *searches for field future and stores field in JavacNode futureField - Throws error if not found
    *delegates creation of method dataReceived(ConnectionEvent evt) and init()
    *injects methods dataReceived(ConnectionEvent evt) and init() of delegated creation
    */
    private void generateMethods(final JavacNode typeNode, final JavacNode source) {

        boolean notAClass = true;
        //check if annotation is on classlevel
        if (typeNode.get() instanceof JCClassDecl) {
            //store simplename of annotated class
            classname = ((JCClassDecl) typeNode.get()).getSimpleName().toString();
            //check that annotation is NOT used on interface, annotation or enum
            final long flags = ((JCClassDecl) typeNode.get()).mods.flags;
            notAClass = (flags & (Flags.INTERFACE | Flags.ANNOTATION | Flags.ENUM )) != 0;
        }
        //if annotation is annotated on non valid target throw error
        if (notAClass) {
            source.addError("@DataReceived is only supported on a class.");
            return;
        }
        //search for field log created by @CommonsLog
        //search for field future need for getContext for adding finalifneeded in forloop
        JavacNode logField = null;
        JavacNode futureField = null;
        //traverse provided ast down to search for fields
        for (final JavacNode field : typeNode.down()) {
            //check if kind node is a field
            if (field.getKind() != AST.Kind.FIELD) continue;
            //cast field to JCVariableDecl
            final JCVariableDecl fieldDecl = (JCVariableDecl) field.get();
            //check if JCVariableDecl is named future if true store field as futureField
            if (fieldDecl.name.toString().equals("future")) {
                futureField = field;
            }
            //Skip fields wich dont named log
            if (!fieldDecl.name.toString().equals("log")) continue;
            //Skip nonstatic fields.
            if ((fieldDecl.mods.flags & Flags.STATIC) == 0) continue;
            //Skip nonfinal fields.
            if ((fieldDecl.mods.flags & Flags.FINAL) == 0) continue;
            //store field as logField
            logField = field;
        }
        //if no logField is found throw error
        if (logField == null) {
            source.addError("Missing @CommonsLog for log variable.");
            return;
        }
        //if no futureField is found throw error
        if (futureField == null) {
            source.addError("FutureField is null. Initiation failed.");
            return;
        }
        //delegate method creation of dataReceived(ConnectionEvent evt) and init() to specific methods
        final JCMethodDecl dataReceiviedMethod = createDataReceivied(typeNode, source.get(), logField, futureField);
        final JCMethodDecl createInit = createInit(typeNode, source.get());
        //inject generated methods dataReceived(ConnectionEvent evt) and init()
        injectMethod(typeNode, createInit);
        injectMethod(typeNode, dataReceiviedMethod);

    }

    /*creates
    *private void init() {
    *    try {
    *        queue = de.sourcepark.smd.base.output.OutputQueueCollection.getInstance().get(Mavenproject1Controller.QUEUE_ID);
    *    } catch (final java.lang.Exception initEx) {
    *        log.error("Field initialization failed. Error occurred.", initEx);
    *    }
    *}
    */
    private JCMethodDecl createInit(final JavacNode typeNode, final JCTree source) {
        //get treemaker to be able to define new ast nodes
        final JavacTreeMaker maker = typeNode.getTreeMaker();
        //store for statements of method init()
        final ListBuffer<JCStatement> initStatements = new ListBuffer<JCStatement>();
        //store for statements of try block
        final ListBuffer<JCStatement> tryStatements = new ListBuffer<JCStatement>();
        //define modifier of method init()
        final JCModifiers mods = maker.Modifiers(Flags.PRIVATE);
        //define return typ of method init()
        final JCExpression returnTypeVoid = maker.Type(Javac.createVoidType(typeNode.getSymbolTable(), CTC_VOID));
        //add statement in try block that executes initialisation of field queue
        tryStatements.append(maker.Exec(maker.Assign(maker.Ident(queueName), queueInitialisation(maker, typeNode))));
        //add try catch block to statements of init()
        initStatements.append(initTryCatch(tryStatements, maker, typeNode));
        //add all statements to method body of init()
        final JCBlock initBlock = maker.Block(0, initStatements.toList());
        //create definition of method init() with specific parameters and validate outcome node
        //returns the definition of the method init() as node
        return recursiveSetGeneratedBy(maker.MethodDef(mods, typeNode.toName("init"), returnTypeVoid, List.<JCTypeParameter>nil(), List.<JCVariableDecl>nil(), List.<JCExpression>nil(), initBlock, null), source, typeNode.getContext());
    }

    /* creates
     * de.sourcepark.smd.base.output.OutputQueueCollection.getInstance().get(Mavenproject1Controller.QUEUE_ID);
     */
    private JCMethodInvocation queueInitialisation(final JavacTreeMaker maker, final JavacNode typeNode) {
        //selects constant QUEUE_ID of named controller via annotation value > controller
        final JCFieldAccess queueStaticString = maker.Select(maker.Ident(elements.getName(controller)), elements.getName("QUEUE_ID"));
        //selects field de.sourcepark.smd.base.output.OutputQueueCollection.getInstance
        final JCFieldAccess getInstanceFA = maker.Select(maker.Ident(elements.getName("de.sourcepark.smd.base.output.OutputQueueCollection")), elements.getName("getInstance"));
        //invoke mehtod de.sourcepark.smd.base.output.OutputQueueCollection.getInstance()
        final JCMethodInvocation queueGetInstance = maker.Apply(List.<JCExpression>nil(), chainDotsString(typeNode, getInstanceFA.toString()), List.<JCExpression>nil());
        //select field de.sourcepark.smd.base.output.OutputQueueCollection.getInstance().get
        final JCFieldAccess getFA = maker.Select(queueGetInstance, elements.getName("get"));
        //invoke de.sourcepark.smd.base.output.OutputQueueCollection.getInstance().get(Mavenproject1Controller.QUEUE_ID);
        //and returns it
        return maker.Apply(List.<JCExpression>nil(), getFA, List.<JCExpression>of(queueStaticString));
    }

    /* creates
     * try {...}
     *      catch (final java.lang.Exception initEx) {
     *        log.error("Field initialization failed. Error occurred.", initEx);
     *    }
     */
    private JCTree.JCTry initTryCatch(final ListBuffer<JCStatement> tryStatements, final JavacTreeMaker maker, final JavacNode typeNode) {
       //adds statements to try block
        final JCBlock tryBlock = maker.Block(0, tryStatements.toList());
        //store for statements of catch block
        final ListBuffer<JCStatement> statementsCatchBlock = new ListBuffer<JCStatement>();
        //create name for exception that could be catched
        final Name initEx = typeNode.toName("initEx");
        //add catch statement wich will executes
        //log.error("Field initialization failed. Error occurred.", initEx);
        statementsCatchBlock.append(maker.Exec(maker.Apply(List.<JCExpression>nil(), chainDotsString(typeNode, "log.error"), List.<JCExpression>of(maker.Literal("Field initialization failed. Error occurred."), maker.Ident(initEx)))));
        //add all catch statements to catch block
        final JCBlock catchBlock = maker.Block(0, statementsCatchBlock.toList());
        //select typ of exception wich should be catched
        final JCFieldAccess ex = maker.Select(maker.Ident(elements.getName("java.lang")), elements.getName("Exception"));
        //define parameter variable of catch witch specific parameters
        final JCVariableDecl smdConfigException = maker.VarDef(maker.Modifiers(Flags.FINAL | Flags.PARAMETER), initEx, ex, null);
        //create catch (..){..}
        final JCCatch jcCatch = maker.Catch(smdConfigException, catchBlock);
        //store for all catch statements of try block - multiple catches could be defined and stored
        final ListBuffer<JCCatch> listCatch = new ListBuffer<JCCatch>();
        //create try{..}catch(..){..}
        return maker.Try(tryBlock, listCatch.append(jcCatch).toList(), null);
    }

    /* creates
     *@java.lang.Override
     *public void dataReceived(final de.sourcepark.smd.ocl.ConnectionEvent evt) {
     * this.init();
     *de.sourcepark.smd.base.util.scp.SCPDataObject scpd = evt.getDataObject();
     * try {
     *    if (scpd.hasStatus()) {
     *       this.doStatusProcessing();
     *       return;
     *    }
     *    if (scpd.isDirty()) {
     *       log.error("Unwanted modification detected. Message is flagged as dirty.");
     *       return;
     *    }
     *    if (scpd.getCommand("<Custom Command>") != null) {
     *       for (final java.util.concurrent.Future f : future) {
     *          if (f.isDone()) {
     *             this.doCleanupOrProcessing(f);
     *          }
     *       }
     *       future.add(processingThreads.submit(new Mavenproject1Controller(scpd)));
     *    } else if (scpd.getCommand("ALIVE") != null) {
     *       de.sourcepark.smd.base.util.scp.SCPStatusObject scps = new de.sourcepark.smd.base.util.scp.SCPStatusObject(scpd);
     *       de.sourcepark.smd.base.util.scp.SCPCommand scpsAddParam = scps.getCommand("ALIVE");
     *       scpsAddParam.addParam(new de.sourcepark.smd.base.util.scp.SCPParameter("Version", "string", de.sourcepark.smd.base.config.SMDConfiguration.getVersionString(Person.class)));
     *       queue.offer(scps);
     *    } else if ((scpd.getCommand("STOP") != null) && scpd.getSource().equalsIgnoreCase(de.sourcepark.smd.base.config.SMDConfiguration.getInstance().getIdentity())) {
     *       this.stop();
     *    } else {
     *       log.error("Unprocessable message received. Possible conflict between command and context. Check message.");
     *    }
     * } catch (final SMDConfigurationException ex) {
     *    log.error("SMDConfigurationException: Configuration error occurred.", ex);
     * } catch (final ConfigurationException ex2) {
     *    log.error("ConfigurationException: Configuration error occurred.", ex2);
     * } catch (final java.lang.Throwable th) {
     *    log.fatal("Unexpected Exception", th);
     * }
     *}
     */
    private JCMethodDecl createDataReceivied(final JavacNode typeNode, final JCTree source, final JavacNode logField, final JavacNode futureField) {
        //get treemaker to be able to define new ast nodes
        final JavacTreeMaker maker = typeNode.getTreeMaker();
        //create name for parametervariable evt
        final Name oName = typeNode.toName("evt");
        //create name for variable scpd
        final Name scpDataObjectName = typeNode.toName("scpd");
        //create name for variable named after simplename of controllerclass specified in annotation
        final Name mavenProject1ControllerName = typeNode.toName(controller);
        //create name for variable this
        final Name thisName = typeNode.toName("this");
        //create name for variable scps
        final Name scpStatusObjectName = typeNode.toName("scps");
        //create name for variable scpsAddParam
        final Name scpsAddParamName = typeNode.toName("scpsAddParam");
        //create @Override for method dataReceived(ConnectionEvent evt){..}
        final JCAnnotation overrideAnnotation = maker.Annotation(genJavaLangTypeRef(typeNode, "Override"), List.<JCExpression>nil());
        //define public modifier for  method dataReceived(ConnectionEvent evt){..}
        final JCModifiers mods = maker.Modifiers(Flags.PUBLIC, List.of(overrideAnnotation));
        //select de.sourcepark.smd.ocl.ConnectionEvent will be used as type of parametervariable
        final JCFieldAccess faConnectionEvent = maker.Select(
                maker.Select(
                        maker.Ident(elements.getName("de.sourcepark.smd")),
                        elements.getName("ocl")),
                elements.getName("ConnectionEvent"));

        //return type of datareceivied
        final JCExpression returnTypeVoid = maker.Type(Javac.createVoidType(typeNode.getSymbolTable(), CTC_VOID));
        //parameter declaration of datareceived
        final JCVariableDecl parameter = maker.VarDef(maker.Modifiers(Flags.FINAL | Flags.PARAMETER), oName, faConnectionEvent, null);
        final List<JCVariableDecl> params = List.of(parameter);
        //Block for all statements in datareceived
        final ListBuffer<JCStatement> statements = new ListBuffer<JCStatement>();
        //add init call to datareceived
        statements.append(maker.Exec(callinit(maker, typeNode, thisName)));
        //add initialisation of scpDataObject to datareceived
        statements.append(initSCP(maker, typeNode, oName, scpDataObjectName));

        //create try statement with specified try block and list of catch blocks
        //add try catch statement to method statements
        final JCTree.JCTry tryCatchBlock = createTryBlock(maker, typeNode, thisName, scpDataObjectName, scpStatusObjectName, scpsAddParamName, mavenProject1ControllerName, futureField, logField);
        statements.append(tryCatchBlock);
        //add method statements to method block
        final JCBlock body = maker.Block(0, statements.toList());
        //return generated method with name, type body
        return recursiveSetGeneratedBy(maker.MethodDef(mods, typeNode.toName("dataReceived"), returnTypeVoid, List.<JCTypeParameter>nil(), params, List.<JCExpression>nil(), body, null), source, typeNode.getContext());
    }

    //short access of typ of java.util.concurrent.Future
    private JCFieldAccess getFutureType(final JavacTreeMaker maker) {
        return maker.Select(maker.Ident(elements.getName("java.util.concurrent")), elements.getName("Future"));
    }

    //shortcut for method invocation used mostly when concatenate method call with parameter is needed
    private JCMethodInvocation callMethodInvo(final JavacNode typeNode, final JavacTreeMaker maker, final String object, final String methodcall, final JCExpression parameter) {
        final JCExpression stringMethod = JavacHandlerUtil.chainDots(typeNode, object, methodcall);
        if (parameter == null) {
            return maker.Apply(List.<JCExpression>nil(), stringMethod, List.<JCExpression>nil());
        } else {
            final List<JCExpression> stringMethodArgs = List.<JCExpression>of(parameter);
            return maker.Apply(List.<JCExpression>nil(), stringMethod, stringMethodArgs);
        }
    }

    /*
     * creates
     * this.init();
     *
     */
    private JCMethodInvocation callinit(final JavacTreeMaker maker, final JavacNode typeNode, final Name thisName) {
        return maker.Apply(List.<JCExpression>nil(), chainDots(typeNode, thisName.toString(), "init"), List.<JCExpression>nil());
    }

    /**
     * creates de.sourcepark.smd.base.util.scp.SCPDataObject scpd = evt.getDataObject();
     */
    private JCVariableDecl initSCP(final JavacTreeMaker maker, final JavacNode typeNode, final Name oName, final Name scpDataObjectName) {

        final JCFieldAccess faSCPDataObject = maker.Select(
                maker.Select(
                        maker.Ident(elements.getName("de.sourcepark.smd.base.util")),
                        elements.getName("scp")),
                elements.getName("SCPDataObject"));
        return maker.VarDef(maker.Modifiers(0), scpDataObjectName, faSCPDataObject, maker.Apply(List.<JCExpression>nil(), chainDots(typeNode, oName.toString(), "getDataObject"), List.<JCExpression>nil()));
    }

    /*
     * creates
     * if (scpd.hasStatus()) {
     * this.doStatusProcessing();
     * return;
     * }
     *
     */
    private JCIf createIfHasStatus(final JavacTreeMaker maker, final JavacNode typeNode, final Name thisName, final Name scpDataObjectName) {

        //invoke scpd.hasStatus()
        final JCMethodInvocation hasStatusInvocation = maker.Apply(List.<JCExpression>nil(), chainDots(typeNode, scpDataObjectName.toString(), "hasStatus"), List.<JCExpression>nil());
        //invoke this.doStatusProcessing()
        final JCMethodInvocation doStatusProcessingInvocation = maker.Apply(List.<JCExpression>nil(), chainDots(typeNode, thisName.toString(), "doStatusProcessing"), List.<JCExpression>nil());
        //store for statements
        final ListBuffer<JCStatement> hasStatusStaments = new ListBuffer<JCStatement>();
        // executes this.doStatusProcessing()
        hasStatusStaments.append(maker.Exec(doStatusProcessingInvocation));
        // executes return;
        hasStatusStaments.append(maker.Return(null));
        //add all statements to if Block
        final JCBlock hastStatusBlock = maker.Block(0, hasStatusStaments.toList());
        //create If statement with scpd.hasStatus() as condition, adds specified block with statements - no else block
        return maker.If(maker.Parens(hasStatusInvocation), hastStatusBlock, null);
    }

    /*
     * creates
     * if (scpd.isDirty()) {
     * log.error("Unwanted modification detected. Message is flagged as dirty.");
     * return;
     * }
     *
     */
    private JCIf createIfIsDirty(final JavacTreeMaker maker, final JavacNode typeNode, final Name scpDataObjectName, final JavacNode logField) {
        //method invocation of scpd.isDirty()
        final JCMethodInvocation isDirtyInvocation = maker.Apply(List.<JCExpression>nil(), chainDots(typeNode, scpDataObjectName.toString(), "isDirty"), List.<JCExpression>nil());
        //method invocation of log.error("Unwanted modification detected. Message is flagged as dirty.");
        final List<JCExpression> logIsDirtyMethodArgs = List.<JCExpression>of(maker.Literal("Unwanted modification detected. Message is flagged as dirty."));
        final JCMethodInvocation logIsDirtyInvocation = maker.Apply(List.<JCExpression>nil(), chainDots(typeNode, logField.getName(), "error"), logIsDirtyMethodArgs);
        //store for statements
        final ListBuffer<JCStatement> isDirtyThenBlockStatements = new ListBuffer<JCStatement>();
        //executes log.error("Unwanted modification detected. Message is flagged as dirty.");
        isDirtyThenBlockStatements.append(maker.Exec(logIsDirtyInvocation));
        //executes return;
        isDirtyThenBlockStatements.append(maker.Return(null));
        //add all statements to if block
        final JCBlock isDirtyThenBlock = maker.Block(0, isDirtyThenBlockStatements.toList());
        //create If statement with scpd.isdirty() as condition, adds specified block with statements - no else block
        return maker.If(maker.Parens(isDirtyInvocation), isDirtyThenBlock, null);
    }

    /*
     * creates scpd.getCommand("STOP") != null
     *
     */
    private JCTree.JCParens createIfComStopConditionOne(final JavacTreeMaker maker, final JavacNode typeNode, final Name scpDataObjectName) {
        //invoke scpd.getCommand("STOP")
        final JCMethodInvocation scpGetCommandSTOPInvo = callMethodInvo(typeNode, maker, scpDataObjectName.toString(), "getCommand", maker.Literal("STOP"));
        //get the outcome of expression: scpd.getCommand("STOP") != null
        return maker.Parens(maker.Binary(CTC_NOT_EQUAL, scpGetCommandSTOPInvo, maker.Literal(CTC_BOT, null)));
    }

    /*
     * creates scpd.getSource().equalsIgnoreCase(SMDConfiguration.getInstance().getIdentity())
     *
     */
    private JCMethodInvocation createIfComStopConditionTwo(final JavacTreeMaker maker, final JavacNode typeNode, final Name scpDataObjectName) {
        //select return value of scpd.getSource as field because of concatenate method call
        final JCFieldAccess scpGetSourceFA = maker.Select(maker.Ident(scpDataObjectName), elements.getName("getSource"));
        //invoke scpd.getSource()
        final JCMethodInvocation scpGetSourceMethodIno = maker.Apply(List.<JCExpression>nil(), chainDotsString(typeNode, scpGetSourceFA.toString()), List.<JCExpression>nil());
        //select return value of scpd.getSource.equalsIgnoreCase as field because of concatenate method call and second condition of if( firstcondition && secondcondition){...}
        final JCFieldAccess equalsIgnoreCaseFA = maker.Select(scpGetSourceMethodIno, elements.getName("equalsIgnoreCase"));
        //select SMDConfiguration.getInstance() as field because of concatenate method call
        final JCFieldAccess smdCGetInstanceFA = maker.Select(maker.Ident(elements.getName("de.sourcepark.smd.base.config.SMDConfiguration")), elements.getName("getInstance"));
        //invoke SMDConfiguration.getInstance()
        final JCMethodInvocation smdCGetInstanceMethodIno = maker.Apply(List.<JCExpression>nil(), chainDotsString(typeNode, smdCGetInstanceFA.toString()), List.<JCExpression>nil());
        //select SMDConfiguration.getInstance().getIdentity as field because of concatenate method call
        final JCFieldAccess smdCGetIdentityeFA = maker.Select(smdCGetInstanceMethodIno, elements.getName("getIdentity"));
        //invoke SMDConfiguration.getInstance().getIdentity()
        final JCMethodInvocation smdCGetIdentityMethodIno = maker.Apply(List.<JCExpression>nil(), smdCGetIdentityeFA, List.<JCExpression>nil());
        //invoke scpd.getSource().equalsIgnoreCase(SMDConfiguration.getInstance().getIdentity())
        return maker.Apply(List.<JCExpression>nil(), equalsIgnoreCaseFA, List.<JCExpression>of(smdCGetIdentityMethodIno));
    }

    /*
     * creates
     * <p>
     * else if ((scpd.getCommand("STOP") != null) && scpd.getSource().equalsIgnoreCase(de.sourcepark.smd.base.config.SMDConfiguration.getInstance().getIdentity())) {
     * this.stop();
     * } else {
     * log.error("Unprocessable message received. Possible conflict between command and context. Check message.");
     * }
     *
     */
    private JCIf createIfComStop(final JavacTreeMaker maker, final JavacNode typeNode, final Name thisName, final Name scpDataObjectName) {
        //get methodinvokation of scpd.getSource().equalsIgnoreCase(SMDConfiguration.getInstance().getIdentity())
        JCMethodInvocation equalsIgnoreCaseMethodInvo = createIfComStopConditionTwo(maker, typeNode, scpDataObjectName);
        //get outcome of scpd.getCommand("STOP") != null
        JCTree.JCParens scptGetSTOPNotNull = createIfComStopConditionOne(maker, typeNode, scpDataObjectName);
        //store for statements of then block
        final ListBuffer<JCStatement> scpdGetC_STOP_Tb_Statements = new ListBuffer<JCStatement>();
        //add statement wich executes this.stop()
        scpdGetC_STOP_Tb_Statements.append(maker.Exec(maker.Apply(List.<JCExpression>nil(), chainDots(typeNode, thisName.toString(), "stop"), List.<JCExpression>nil())));
        //add statements to thenblock
        final JCBlock scpdGetCommandSTOPThenBlock = maker.Block(0, scpdGetC_STOP_Tb_Statements.toList());

        //store for statements of else block
        final ListBuffer<JCStatement> scpdGetC_STOP_Eb_Statements = new ListBuffer<JCStatement>();
        //add statement wich executes log.error("Unprocessable message received. Possible conflict between command and context. Check message.");
        scpdGetC_STOP_Eb_Statements.append(maker.Exec(maker.Apply(List.<JCExpression>nil(), chainDotsString(typeNode, "log.error"), List.<JCExpression>of(maker.Literal("Unprocessable message received. Possible conflict between command and context. Check message.")))));
        //add statementsto elseblock
        final JCBlock scpdGetCommandSTOPElseBlock = maker.Block(0, scpdGetC_STOP_Eb_Statements.toList());
        //create If statement with (scpd.getCommand("STOP") != null) && scpd.getSource().equalsIgnoreCase(de.sourcepark.smd.base.config.SMDConfiguration.getInstance().getIdentity())
        //as conditions, adds specified then and else block
        return maker.If(maker.Parens(maker.Binary(CTC_AND, scptGetSTOPNotNull, equalsIgnoreCaseMethodInvo)), scpdGetCommandSTOPThenBlock, scpdGetCommandSTOPElseBlock);
    }

    /*
     * creates
     * else if (scpd.getCommand("ALIVE") != null) {
     * de.sourcepark.smd.base.util.scp.SCPStatusObject scps = new de.sourcepark.smd.base.util.scp.SCPStatusObject(scpd);
     * de.sourcepark.smd.base.util.scp.SCPCommand scpsAddParam = scps.getCommand("ALIVE");
     * scpsAddParam.addParam(new de.sourcepark.smd.base.util.scp.SCPParameter("Version", "string", de.sourcepark.smd.base.config.SMDConfiguration.getVersionString(Person.class)));
     * queue.offer(scps);
     *
     */
    private JCIf createIfComAlive(final JavacTreeMaker maker, final JavacNode typeNode, final Name scpDataObjectName, final Name scpStatusObjectName, final Name scpsAddParamName, final JCIf scpdGetCommandSTOP, final JCExpression selectscp) {

        //store for statements
        final ListBuffer<JCStatement> scpGetCommandALIVEstaments = new ListBuffer<JCStatement>();
        //select class SCPStatusObject in package de.sourcepark.smd.base.util.scp
        final JCFieldAccess sCPStatusObject = maker.Select(maker.Ident(elements.getName("de.sourcepark.smd.base.util.scp")), elements.getName("SCPStatusObject"));
        //create new instance of class SCPStatusObject with scpd as constructor parameter - scpd has to be selected via fieldaccess
        final JCNewClass scpsC = maker.NewClass(null, List.<JCExpression>nil(), sCPStatusObject, List.of(selectscp), null);
        //declare new local variable scps of type de.sourcepark.smd.base.util.scp.SCPStatusObject initialized with new SCPStatusObject(scpd)
        final JCVariableDecl scpStatusObject = maker.VarDef(maker.Modifiers(0), scpStatusObjectName, sCPStatusObject, scpsC);
        //add statement
        scpGetCommandALIVEstaments.append(scpStatusObject);

        //select class SCPParameter in package de.sourcepark.smd.base.util.scp
        final JCFieldAccess sCPParameter = maker.Select(maker.Ident(elements.getName("de.sourcepark.smd.base.util.scp")), elements.getName("SCPParameter"));
        //create string "Version"
        final JCExpression version = maker.Literal("Version");
        //create string "string"
        final JCExpression stringP = maker.Literal("string");
        //select class name of annotated class
        final JCFieldAccess mp1C = maker.Select(maker.Ident(elements.getName(classname)), elements.getName("class"));
        //invoke de.sourcepark.smd.base.config.SMDConfiguration.getVersionString(Person.class)
        final JCMethodInvocation getVersionString = maker.Apply(List.<JCExpression>nil(), chainDotsString(typeNode, "de.sourcepark.smd.base.config.SMDConfiguration.getVersionString"), List.<JCExpression>of(mp1C));
        //create new instance of de.sourcepark.smd.base.util.scp.SCPParameter with "Version", "string" and name of annotated class as parameter
        final JCExpression scpParameter = maker.NewClass(null, List.<JCExpression>nil(), sCPParameter, List.of(version, stringP, getVersionString), null);
        //select class SCPCommand in package de.sourcepark.smd.base.util.scp
        final JCFieldAccess sCPCommand = maker.Select(maker.Ident(elements.getName("de.sourcepark.smd.base.util.scp")), elements.getName("SCPCommand"));
        //declare new local variable scpsAddParam of type SCPCommand initialized with scps.getCommand("ALIVE")
        final JCVariableDecl scpCommandObject = maker.VarDef(maker.Modifiers(0), scpsAddParamName, sCPCommand, callMethodInvo(typeNode, maker, scpStatusObjectName.toString(), "getCommand", maker.Literal("ALIVE")));
        //add statement
        scpGetCommandALIVEstaments.append(scpCommandObject);
        //add statement of execution of method invokation -> scpsAddParam.addParam(new de.sourcepark.smd.base.util.scp.SCPParameter("Version", "string", de.sourcepark.smd.base.config.SMDConfiguration.getVersionString(<annotated class>)));
        scpGetCommandALIVEstaments.append(maker.Exec(maker.Apply(List.<JCExpression>nil(), chainDots(typeNode, scpsAddParamName.toString(), "addParam"), List.of(scpParameter))));

        //add statement of execution of method invokation -> queue.offer(scps);
        scpGetCommandALIVEstaments.append(maker.Exec(callMethodInvo(typeNode, maker, "queue", "offer", maker.Ident(scpStatusObjectName))));

        //this is just for logging
        //add statement of execution of mehtod invokation ->  log.info(In IfScpGetCommand ALIVE != null)
        //scpGetCommandALIVEstaments.append(maker.Exec(maker.Apply(List.<JCExpression>nil(), chainDotsString(typeNode, "log.info"), List.<JCExpression>of(maker.Literal("In IfScpGetCommand ALIVE != null")))));

        //invoke scpd.getCommand("ALIVE")
        final JCMethodInvocation scpGetCommandALIVEInvo = callMethodInvo(typeNode, maker, scpDataObjectName.toString(), "getCommand", maker.Literal("ALIVE"));
        //thenBlock with all statements
        final JCBlock scpGetCommandALIVEThenBlock = maker.Block(0, scpGetCommandALIVEstaments.toList());
        //create If statement with if (scpd.getCommand("ALIVE") != null)
        //as conditions, adds specified then and else block
        return maker.If(maker.Parens(maker.Binary(CTC_NOT_EQUAL, scpGetCommandALIVEInvo, maker.Literal(CTC_BOT, null))), scpGetCommandALIVEThenBlock, scpdGetCommandSTOP);
    }

    /*
     * creates
     * if (scpd.getCommand("<Custom Command>") != null) {
     * for (final java.util.concurrent.Future f : future) {
     * if (f.isDone()) {
     * this.doCleanupOrProcessing(f);
     * }
     * }
     * future.add(processingThreads.submit(new <custom controller class specified in annotation>(scpd)));
     *
     */
    private JCIf createIfComCustom(final JavacTreeMaker maker, final JavacNode typeNode, final Name thisName, final Name scpDataObjectName, final Name mavenProject1ControllerName, final JavacNode futureField, final JCExpression selectscp, final JCIf ifScpCommandAlive) {

        //create name of variable f for forLoop
        final Name varForLoop = typeNode.toName("f");
        //invoke f.isDone()
        final JCMethodInvocation isDoneInvocation = maker.Apply(List.<JCExpression>nil(), chainDots(typeNode, varForLoop.toString(), "isDone"), List.<JCExpression>nil());
        //invoke this.doCleanupOrProcessing()
        final JCMethodInvocation externDoCleanUPInvocation = maker.Apply(List.<JCExpression>nil(), chainDots(typeNode, thisName.toString(), "doCleanupOrProcessing"), List.<JCExpression>of(maker.Ident(varForLoop)));
        //store for statements of then block in if(f.isDone()) {...}
        final ListBuffer<JCStatement> isDoneStaments = new ListBuffer<JCStatement>();
        //add statement of execution of this.doCleanupOrProcessing()
        isDoneStaments.append(maker.Exec(externDoCleanUPInvocation));
        //add statements to then block of if (f.isDone()) {...}
        final JCBlock isDoneBlock = maker.Block(0, isDoneStaments.toList());
        //create if (f.isDone()) {...} with f.isDone() as condition and spezified then block
        final JCIf forLoopFisDone = maker.If(maker.Parens(isDoneInvocation), isDoneBlock, null);

        //store for loopstatements
        final ListBuffer<JCStatement> forLoopStatements = new ListBuffer<JCStatement>();
        //add statement of if (f.isDone()) {...} to forloop statements
        forLoopStatements.append(forLoopFisDone);
        //create block of forloop
        final JCBlock forBlock = maker.Block(0, forLoopStatements.toList());
        //create flag final of forloop variable
        final long baseFlags = JavacHandlerUtil.addFinalIfNeeded(0, futureField.getContext());
        //create forEach loop -> for (final java.util.concurrent.Future f : future) {...}
        final JCEnhancedForLoop getCommandForEachLoop = maker.ForeachLoop(maker.VarDef(maker.Modifiers(baseFlags), varForLoop, getFutureType(maker), null), maker.Ident(typeNode.toName(futureField.getName())), forBlock);
        //store for thenblock statements
        final ListBuffer<JCStatement> getCommandThenStatements = new ListBuffer<JCStatement>();
        //add forloop statement to thenstatements
        getCommandThenStatements.append(getCommandForEachLoop);

        //create new instance of annotation value()[1] with scpd as parameter
        final JCNewClass mp1c = maker.NewClass(null, List.<JCExpression>nil(), maker.Ident(mavenProject1ControllerName), List.of(selectscp), null);
        //invoke processingThreads.submit(new <class named at annotation value()[1]>(scpd))
        final JCMethodInvocation submitInvocation = maker.Apply(List.<JCExpression>nil(), chainDots(typeNode, processingThreadsName.toString(), "submit"), List.<JCExpression>of(mp1c));
        //invoke futures.add(processingThreads.submit(new <class named at annotation value()[1]>(scpd)));
        final JCMethodInvocation addInvocation = maker.Apply(List.<JCExpression>nil(), chainDots(typeNode, futureField.getName(), "add"), List.<JCExpression>of(submitInvocation));
        //add statement of executuon of futures.add(processingThreads.submit(new <class named at annotation value()[1]>(scpd))); to then block
        getCommandThenStatements.append(maker.Exec(addInvocation));

        //invoke scpd.getCommand("<command id named at annotation value[0]>")
        final JCMethodInvocation scpdCommandInvocation = maker.Apply(List.<JCExpression>nil(), chainDots(typeNode, scpDataObjectName.toString(), "getCommand"), List.<JCExpression>of(maker.Literal(command)));
        //then block with all statements
        final JCBlock getCommandThenBlock = maker.Block(0, getCommandThenStatements.toList());
        //create If statement with if (scpd.getCommand("<command id named at annotation value[0]>") != null)
        //as conditions, adds specified then and else block
        return maker.If(maker.Parens(maker.Binary(CTC_NOT_EQUAL, scpdCommandInvocation, maker.Literal(CTC_BOT, null))), getCommandThenBlock, ifScpCommandAlive);
    }

    /* creates
     *  catch (final SMDConfigurationException ex) {
     *     log.error("SMDConfigurationException: Configuration error occurred.", ex);
     *  } catch (final ConfigurationException ex2) {
     *     log.error("ConfigurationException: Configuration error occurred.", ex2);
     *  } catch (final java.lang.Throwable th) {
     *     log.fatal("Unexpected Exception", th);
     *  }
     */
    private ListBuffer<JCCatch> createCatchBlocks(final JavacTreeMaker maker, final JavacNode typeNode) {

        //select specified types of exceptions
        final JCFieldAccess smdConfigEx = maker.Select(maker.Ident(elements.getName("de.sourcepark.smd.base.config")), elements.getName("SMDConfigurationException"));
        final JCFieldAccess configEx = maker.Select(maker.Ident(elements.getName("org.apache.commons.configuration")), elements.getName("ConfigurationException"));
        //select specified typ of throwable
        final JCFieldAccess throwEx = maker.Select(maker.Ident(elements.getName("java.lang")), elements.getName("Throwable"));
        //creates names for exceptions/throwable nodes
        final Name exName = typeNode.toName("ex");
        final Name ex2Name = typeNode.toName("ex2");
        final Name throwName = typeNode.toName("th");
        //creates final SMDConfigurationException ex
        final JCVariableDecl smdConfigException = maker.VarDef(maker.Modifiers(Flags.FINAL | Flags.PARAMETER), exName, smdConfigEx, null);
        //creates final ConfigurationException ex2
        final JCVariableDecl configExException = maker.VarDef(maker.Modifiers(Flags.FINAL | Flags.PARAMETER), ex2Name, configEx, null);
        //creates final java.lang.Throwable th
        final JCVariableDecl throwable = maker.VarDef(maker.Modifiers(Flags.FINAL | Flags.PARAMETER), throwName, throwEx, null);
        //3 stores of statements for 3 exceptions blocks
        final ListBuffer<JCStatement> statementsCatchBlock1 = new ListBuffer<JCStatement>();
        final ListBuffer<JCStatement> statementsCatchBlock2 = new ListBuffer<JCStatement>();
        final ListBuffer<JCStatement> statementsCatchBlock3 = new ListBuffer<JCStatement>();
        //invoke log.error("SMDConfigurationException: Configuration error occurred.", ex);
        final JCMethodInvocation exLog1 = maker.Apply(List.<JCExpression>nil(), chainDotsString(typeNode, "log.error"), List.<JCExpression>of(maker.Literal("SMDConfigurationException: Configuration error occurred."), maker.Ident(exName)));
        //invoke log.error("ConfigurationException: Configuration error occurred.", ex2);
        final JCMethodInvocation exLog2 = maker.Apply(List.<JCExpression>nil(), chainDotsString(typeNode, "log.error"), List.<JCExpression>of(maker.Literal("ConfigurationException: Configuration error occurred."), maker.Ident(ex2Name)));
        //invoke log.fatal("Unexpected Exception", th);
        final JCMethodInvocation exLog4 = maker.Apply(List.<JCExpression>nil(), chainDotsString(typeNode, "log.fatal"), List.<JCExpression>of(maker.Literal("Unexpected Exception"), maker.Ident(throwName)));
        //add statements of SMDConfigurationException to SMDConfigurationException catch block
        statementsCatchBlock1.append(maker.Exec(exLog1));
        final JCBlock catchBlock1 = maker.Block(0, statementsCatchBlock1.toList());
        final JCCatch jcCatch1 = maker.Catch(smdConfigException, catchBlock1);
        //add statements of ConfigurationException to ConfigurationException catch block
        statementsCatchBlock2.append(maker.Exec(exLog2));
        final JCBlock catchBlock2 = maker.Block(0, statementsCatchBlock2.toList());
        final JCCatch jcCatch2 = maker.Catch(configExException, catchBlock2);
        //add statements of Throwable to Throwable catch block
        statementsCatchBlock3.append(maker.Exec(exLog4));
        final JCBlock catchBlock3 = maker.Block(0, statementsCatchBlock3.toList());
        final JCCatch jcCatch3 = maker.Catch(throwable, catchBlock3);
        //store for catch blocks
        final ListBuffer<JCCatch> listCatch = new ListBuffer<JCCatch>();
        //add all catch blocks to list of catch blocks
        listCatch.append(jcCatch1);
        listCatch.append(jcCatch2);
        listCatch.append(jcCatch3);
        return listCatch;
    }

    /*creates
    * try{ .... }
    * catch { ...}
    * catch { ...}
    * catch { ...}
     */
    private JCTree.JCTry createTryBlock(final JavacTreeMaker maker, final JavacNode typeNode, final Name thisName, final Name scpDataObjectName, final Name scpStatusObjectName, final Name scpsAddParamName, final Name mavenProject1ControllerName, final JavacNode futureField, final JavacNode logField) {

        //try block begins here
        final ListBuffer<JCStatement> statementsInTryBlock = new ListBuffer<JCStatement>();
        //add if(scpd.hasStatus()){...} to try block
        statementsInTryBlock.append(createIfHasStatus(maker, typeNode, thisName, scpDataObjectName));
        //add if(scpd.isDirty()){...} to try block
        statementsInTryBlock.append(createIfIsDirty(maker, typeNode, scpDataObjectName, logField));

        //select scpDataObject as Ident
        final JCExpression selectscp = maker.Ident(scpDataObjectName);

        // creates the following structures with defined conditions and blocks, beginning with else and ending with if
        // if(...){...}
        // else if(...){...}
        // else if(..){...}
        // else{...}
        final JCIf scpdGetCommandSTOP = createIfComStop(maker, typeNode, thisName, scpDataObjectName);
        final JCIf ifScpCommandAlive = createIfComAlive(maker, typeNode, scpDataObjectName, scpStatusObjectName, scpsAddParamName, scpdGetCommandSTOP, selectscp);
        final JCIf ifScpdCommand = createIfComCustom(maker, typeNode, thisName, scpDataObjectName, mavenProject1ControllerName, futureField, selectscp, ifScpCommandAlive);

        //adds statements of if ... else if ... else to store of try statements
        statementsInTryBlock.append(ifScpdCommand);
        //TryBlock add all try statements to try block
        final JCBlock tryBlock = maker.Block(0, statementsInTryBlock.toList());
        return maker.Try(tryBlock, createCatchBlocks(maker, typeNode).toList(), null);
    }

}

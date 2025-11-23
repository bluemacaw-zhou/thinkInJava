package io.bluemacaw.mongodb;

import com.mongodb.client.result.UpdateResult;
import io.bluemacaw.mongodb.entity.Employee;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.BasicQuery;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Slf4j
@SpringBootTest
public class TemplateTest {
    @Resource
    MongoTemplate mongoTemplate;

    @Test
    public void testCreateCollection(){
        boolean exists = mongoTemplate.collectionExists("emp");
        if(exists){
//            mongoTemplate.dropCollection("emp");
            log.info("emp collection already exists");
        }

        mongoTemplate.createCollection("emp");
        log.info("create emp collection success");
    }

    @Test
    public void testInsert(){
        // 添加文档
        // sava:  _id存在时更新数据
        // mongoTemplate.save(employee);
        LocalDateTime now = LocalDateTime.now();
        Employee employee = new Employee(1, "小明", 30,10000.00, now);

        // 插入单条数据
        mongoTemplate.save(employee);

        // 加上老张 测试后续的正则查询
        Employee anotherEmployee = new Employee(8, "老张",38, 2000.00, now);
        mongoTemplate.save(anotherEmployee);

        // insert： _id存在抛出异常   支持批量操作
        // mongoTemplate.insert(employee);
        List<Employee> list = Arrays.asList(
                new Employee(2, "张三", 21,5000.00, now),
                new Employee(3, "李四", 26,8000.00, now),
                new Employee(4, "王五",22, 8000.00, now),
                new Employee(5, "张龙",28, 6000.00, now),
                new Employee(6, "赵虎",24, 7000.00, now),
                new Employee(7, "赵六",28, 12000.00, now));

        // 插入多条数据
        mongoTemplate.insert(list,Employee.class);
    }

    @Test
    public void testFind(){
        // 查询所有文档
//        log.info("==========查询所有文档===========");
//        List<Employee> list = mongoTemplate.findAll(Employee.class);
//        log.info("{}", list);

        // 根据_id查询
//        log.info("==========根据_id查询===========");
//        Employee e = mongoTemplate.findById(1, Employee.class);
//        log.info("{}", e);

        // 如果查询结果是多个，返回其中第一个文档对象
//        log.info("==========findOne返回第一个文档===========");
//        Employee e = mongoTemplate.findOne(new Query(), Employee.class);
//        log.info("{}", e);


        log.info("==========条件查询===========");
        // new Query() 表示没有条件
//        Query query = new Query();


        //---------------------------------


        // 查询薪资大于等于8000的员工
//        Query query = new Query(Criteria.where("salary").gte(8000));

        // 查询薪资大于4000小于10000的员工
//        Query query = new Query(Criteria.where("salary").gt(4000).lt(10000));

        // 正则查询（模糊查询）  java中正则不需要有//
//        Query query = new Query(Criteria.where("name").regex("张"));


        //---------------------------------


        // QBC => Query By Criteria
        // and / or => 多条件查询
        Criteria criteria = new Criteria();

        // and  查询年龄大于25&薪资大于8000的员工
//        criteria.andOperator(Criteria.where("age").gt(25),Criteria.where("salary").gt(8000));

        // or 查询姓名是张三或者薪资大于8000的员工
//        criteria.orOperator(Criteria.where("name").is("张三"),Criteria.where("salary").gt(5000));

        Query query = new Query(criteria);

        // sort排序
//        query.with(Sort.by(Sort.Order.desc("salary")));

        //skip limit 分页  skip用于指定跳过记录数，limit则用于限定返回结果数量。
        query.with(Sort.by(Sort.Order.desc("salary")))
                .skip(0)    //指定跳过记录数
                .limit(4);  //每页显示记录数

        List<Employee> employees = mongoTemplate.find(query, Employee.class);
        log.info("{}", employees);
    }

    @Test
    public void testFindByJson() {
        // 使用json字符串方式查询
        // 等值查询
        // String json = "{name:'张三'}";
        // 多条件查询
        String json = "{$or:[{age:{$gt:25}},{salary:{$gte:8000}}]}";
        Query query = new BasicQuery(json);

        // 查询结果
        List<Employee> employees = mongoTemplate.find(query, Employee.class);
        log.info("{}", employees);
    }

    @Test
    public void testUpdate(){
        log.info("==========更新前===========");

        // query设置查询条件
        Query query = new Query(Criteria.where("salary").gte(5000));
        List<Employee> employees = mongoTemplate.find(query, Employee.class);
        log.info("{}", employees);

        // 设置更新属性
        Update update = new Update();
        update.set("salary",14000);

        // updateFirst() 只更新满足条件的第一条记录
//        UpdateResult updateResult = mongoTemplate.updateFirst(query, update, Employee.class);

        // updateMulti() 更新所有满足条件的记录
        UpdateResult updateResult = mongoTemplate.updateMulti(query, update, Employee.class);

        // TODO: 不符预期
        // 指定id
//        update.setOnInsert("id", 11);

        // upsert() 没有符合条件的记录则插入数据
//        UpdateResult updateResult = mongoTemplate.upsert(query, update, Employee.class);

        //返回修改的记录数
        log.info("modify count: {}", updateResult.getModifiedCount());

        log.info("==========更新后===========");
        employees = mongoTemplate.find(query, Employee.class);
        log.info("{}", employees);
    }

    @Test
    public void testDelete(){
        // 删除所有文档   不如用dropCollection()
        // mongoTemplate.remove(new Query(), Book.class);

        // 条件删除
        Query query = new Query(Criteria.where("salary").gte(10000));
        mongoTemplate.remove(query,Employee.class);
    }
}

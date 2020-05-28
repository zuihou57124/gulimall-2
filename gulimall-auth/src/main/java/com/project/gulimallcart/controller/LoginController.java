package com.project.gulimallcart.controller;

import com.alibaba.fastjson.TypeReference;
import com.project.gulimallcart.constant.AuthConst;
import com.project.gulimallcart.feign.MemberFeignService;
import com.project.gulimallcart.feign.ThirdPartyFeignService;
import com.project.gulimallcart.vo.LoginVo;
import com.project.gulimallcart.vo.RegisterVo;
import io.renren.common.myconst.MyConst;
import io.renren.common.utils.R;
import io.renren.common.vo.MemberRespVo;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.servlet.http.HttpSession;
import javax.validation.Valid;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @author qcw
 */
@Controller
public class LoginController {


    @Autowired
    ThirdPartyFeignService thirdPartyFeignService;

    @Autowired
    RedisTemplate redisTemplate;

    @Autowired
    MemberFeignService memberFeignService;


    @RequestMapping("/login.html")
    public String loginPage(HttpSession session){

        Object user = session.getAttribute(AuthConst.LOGIN_USER);
        if(user==null){
            return "login";
        }
        return "redirect:http://gulimall.com";
    }


    @PostMapping("/login")
    public String login(RedirectAttributes model, LoginVo loginVo
    , HttpSession session){

        R r = memberFeignService.login(loginVo);
        if(r.getCode()!=0){
            Map<String, String> resultMap = new HashMap<>();
            resultMap.put("errors", (String) r.get("msg"));
            model.addFlashAttribute("errors",resultMap);
            return "redirect:http://auth.gulimall.com/login.html";
        }
        session.setAttribute(AuthConst.LOGIN_USER,r.getData("data", new TypeReference<MemberRespVo>(){}));
        return "redirect:http://gulimall.com";
    }


    @PostMapping("/register")
    public String register(RedirectAttributes model, @Valid RegisterVo registerVo, BindingResult result){
        
        if(result.hasErrors()){
            //校验出错，返回注册页面，并给出提示
            Map<String, String> resultMap = result.getFieldErrors().stream().collect(Collectors.toMap(FieldError::getField, DefaultMessageSourceResolvable::getDefaultMessage));
            //model.addAttribute("errors",resultMap);
            model.addFlashAttribute("errors",resultMap);
            // 转发会原封不动的将请求转发，包括请求方式
            return "redirect:http://auth.gulimall.com/register.html";
        }

        //校验验证码，如果正确，远程调用会员服务，存储用户注册信息
        String redisCode = (String) redisTemplate.opsForValue().get(AuthConst.SMS_CODE_PREFIX+registerVo.getPhone());
        if(!StringUtils.isEmpty(registerVo.getCode()) && !StringUtils.isEmpty(redisCode) ){
            if (registerVo.getCode().equals(redisCode.split("_")[0])){
                //验证码正确后，删除验证码
                redisTemplate.delete(AuthConst.SMS_CODE_PREFIX+registerVo.getPhone());
                //远程调用会员服务,进行注册
                R r = memberFeignService.regsiter(registerVo);
                if(r.getCode()== MyConst.MemberEnum.HAS_PHONE_EXCEPTION.getCode()){
                    Map<String, String> resultMap = new HashMap<>();
                    resultMap.put("phone", (String) r.get("msg"));
                    model.addFlashAttribute("errors",resultMap);
                    System.out.println(r.get("msg"));
                    return "redirect:http://auth.gulimall.com/register.html";
                }
                else if(r.getCode()== MyConst.MemberEnum.HAS_USER_EXCEPTION.getCode()){
                    Map<String, String> resultMap = new HashMap<>();
                    resultMap.put("userName", (String) r.get("msg"));
                    model.addFlashAttribute("errors",resultMap);
                    System.out.println(r.get("msg"));
                    return "redirect:http://auth.gulimall.com/register.html";
                }
                return "login";
            }
            else {
                Map<String, String> resultMap = new HashMap<>();
                resultMap.put("code","验证码错误或者已过期");
                model.addFlashAttribute("errors",resultMap);
                return "redirect:http://auth.gulimall.com/register.html";
            }
        }
        else {
            Map<String, String> resultMap = new HashMap<>();
            resultMap.put("errors","验证码错误或者已过期");
            model.addFlashAttribute("errors",resultMap);
            return "redirect:http://auth.gulimall.com/register.html";
        }

    }



    @RequestMapping("/sms/sendcode")
    @ResponseBody
    public R sendCode(@RequestParam("phone") String phone){

        if(StringUtils.isEmpty(phone)){
            return R.error(AuthConst.SmsEnum.SMS_SEND_PHONENUM_NULL.getCode(),AuthConst.SmsEnum.SMS_SEND_PHONENUM_NULL.getMsg());
        }

        String redisCode = (String) redisTemplate.opsForValue().get(AuthConst.SMS_CODE_PREFIX+phone);
        if(!StringUtils.isEmpty(redisCode)){
            System.out.println("验证码是-----"+redisCode.split("_")[0]);
            long lastTime = Long.parseLong(redisCode.split("_")[1]);
            if(System.currentTimeMillis()-lastTime<=60000){
                return R.error(AuthConst.SmsEnum.SMS_SEND_NOTIME.getCode(),AuthConst.SmsEnum.SMS_SEND_NOTIME.getMsg());
            }
        }

        //1.接口防刷
        //2.判断验证码是否正确  redis
        //验证码存储时要加上时间戳，而发送短信时不用加
        String code = UUID.randomUUID().toString().substring(0,4)+"_"+System.currentTimeMillis();
        System.out.println("验证码是-----"+code.split("_")[0]);
        //thirdPartyFeignService.sendCode(phone,code.split("_")[0]);
        redisTemplate.opsForValue().set(AuthConst.SMS_CODE_PREFIX+phone,code,5, TimeUnit.MINUTES);
        return R.ok();
    }

}

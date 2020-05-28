package com.project.gulimallcart.controller;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.project.gulimallcart.feign.MemberFeignService;
import com.project.gulimallcart.utils.HttpUtils;
import io.renren.common.vo.MemberRespVo;
import com.project.gulimallcart.vo.SocialUserVo;
import io.renren.common.utils.R;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpResponse;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;

/**
 * @author qcw
 * 处理社交登录请求
 */
@Slf4j
@Controller
@RequestMapping("auth")
public class AuthController {

    @Autowired
    MemberFeignService memberFeignService;

    @RequestMapping("/weibo/success")
    public String weibo(@RequestParam("code") String code, HttpSession session){

        //换取access_token
        Map<String,String> map = new HashMap<>();
        Map<String,String> headers = new HashMap<>();
        map.put("client_id","2403309464");
        map.put("client_secret","f88459437fcd664135bc8f6cca232a6f");
        map.put("grant_type","authorization_code");
        map.put("redirect_uri","http://auth.gulimall.com/auth/weibo/success");
        map.put("code",code);

        try {
            HttpResponse response = HttpUtils.doPost("https://api.weibo.com", "/oauth2/access_token"
                    , "post", headers, null, map);
            if (response.getStatusLine().getStatusCode()==200){
                //获取access_token
                String jsonRes = EntityUtils.toString(response.getEntity());
                SocialUserVo socialUser = JSON.parseObject(jsonRes, SocialUserVo.class);
                //如果是第一次用微博登录，会生成一个对应的会员信息，下次登录时会判断是否存在
                R r = memberFeignService.socialLogin(socialUser);
                if(r.getCode()==0){
                    MemberRespVo member = r.getData("data", new TypeReference<MemberRespVo>() {});
                    session.setAttribute("user",member);
                    log.info("登录成功,用户信息是--"+member.toString());
                    return "redirect:http://gulimall.com";
                }
                else {
                    System.out.println("远程调用会员微服务出错");
                    return "redirect:http://auth.gulimall.com/login.html";
                }
            }else {
                return "redirect:http://auth.gulimall.com/login.html";
            }
        } catch (Exception e) {
            System.out.println("请求微博服务器出错");
            e.printStackTrace();
        }

        return "redirect:http://auth.gulimall.com/login.html";
    }

}

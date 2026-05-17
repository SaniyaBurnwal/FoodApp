package com.phegon.FoodApp.review.services;

import com.phegon.FoodApp.auth_users.entity.User;
import com.phegon.FoodApp.auth_users.services.UserService;
import com.phegon.FoodApp.enums.OrderStatus;
import com.phegon.FoodApp.exceptions.BadRequestException;
import com.phegon.FoodApp.exceptions.NotFoundException;
import com.phegon.FoodApp.menu.entity.Menu;
import com.phegon.FoodApp.menu.repository.MenuRepository;
import com.phegon.FoodApp.order.entity.Order;
import com.phegon.FoodApp.order.repository.OrderItemRepository;
import com.phegon.FoodApp.order.repository.OrderRepository;
import com.phegon.FoodApp.response.Response;
import com.phegon.FoodApp.review.dtos.ReviewDTO;
import com.phegon.FoodApp.review.entity.Review;
import com.phegon.FoodApp.review.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReviewServiceImpl implements ReviewService{


    private final ReviewRepository reviewRepository;
    private final MenuRepository menuRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ModelMapper modelMapper;
    private final UserService userService;

    @Override
    @Transactional
    public Response<ReviewDTO> createReview(ReviewDTO reviewDTO) {

        log.info("Inside createReview()");
        User user=userService.getCurrentLoggedInUser();
        if(reviewDTO.getOrderId() ==null || reviewDTO.getMenuId()==null){
            throw new BadRequestException("Order id and menu item id are required");
        }
        Menu menu=menuRepository.findById(reviewDTO.getMenuId())
                .orElseThrow(()-> new NotFoundException("Menu item not found"));

        Order order=orderRepository.findById(reviewDTO.getOrderId())
                .orElseThrow(()-> new NotFoundException("Order not found"));

       if (!order.getUser().getId().equals(user.getId())){
           throw new BadRequestException("This order doesn't belong to you");
       }
       if (order.getOrderStatus() != OrderStatus.DELIVERED){
           throw new BadRequestException("You can only receive items from delivered orders");
       }

       boolean itemInOrder=orderItemRepository.existsByOrderIdAndMenuId(
               reviewDTO.getOrderId(),
               reviewDTO.getMenuId()
       );
       if(!itemInOrder){
           throw new BadRequestException("This menu item was not part of the specified order");
       }
       if (reviewRepository.existsByUserIdAndMenuIdAndOrderId(
               user.getId(),
               reviewDTO.getMenuId(),
               reviewDTO.getOrderId()
       )){
           throw new BadRequestException("You have already reviewed this item from this order");
       }

        Review review= Review.builder()
                .user(user)
                .menu(menu)
                .orderId(reviewDTO.getOrderId())
                .rating(reviewDTO.getRating())
                .comment(reviewDTO.getComment())
                .createdAt(LocalDateTime.now())
                .build();

        Review savedReview = reviewRepository.save(review);
        ReviewDTO responseDto = modelMapper.map(savedReview, ReviewDTO.class);
        responseDto.setUserName(user.getName());
        responseDto.setMenuName(menu.getName());

        return Response.<ReviewDTO>builder()
                .statusCode(HttpStatus.OK.value())
                .message ("Review added successfully")
                .data(responseDto)
                .build();
    }

    @Override
    public Response<List<ReviewDTO>> getReviewsForMenu(Long menuId) {
        log.info("Inside getReviewsForMenu()");

        List<Review> reviews = reviewRepository.findByMenuIdOrderByIdDesc(menuId);

        List<ReviewDTO> reviewDTOs = reviews.stream()
                .map( review -> modelMapper.map (review, ReviewDTO.class))
                .toList();

        return Response.<List<ReviewDTO>>builder()
                .statusCode(HttpStatus.OK.value())
                .message ("Reviews retrieved successfully")
                .data(reviewDTOs)
                .build();
    }

    @Override
    public Response<Double> getAverageRating(Long menuId) {

        log.info("Inside getAverageRating()");

        Double averageRating = reviewRepository.calculateAverageRatingByMenuId(menuId);

        return Response.<Double>builder()
                .statusCode(HttpStatus.OK.value())
                .message("Average rating retrieved successfully")
                .data(averageRating != null ? averageRating: 0.0)
                .build();
    }
}
